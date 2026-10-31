package com.androidlearning.sliide_challenge.userfeed.data

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.androidlearning.sliide_challenge.core.database.DatabaseTransactionRunner
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserDao
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserEntity
import com.androidlearning.sliide_challenge.core.database.user.DeleteUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UpdateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UserDao
import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.core.database.user.UserPendingSyncDao
import com.androidlearning.sliide_challenge.core.domain.util.ApplicationScope
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.PendingUserChange
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstUserFeedRepositoryTest {

    private class FakeUserFeedApi : UserFeedApi {
        var lastPageResult: Result<List<UserDto>, DataError.Network> = Result.Success(emptyList())
        var createResult: Result<UserDto, DataError.Network> = Result.Success(UserDto(100, "", ""))
        var updateResult: Result<UserDto, DataError.Network> = Result.Success(UserDto(1, "", ""))
        var findResult: Result<UserDto?, DataError.Network> = Result.Success(null)
        var deleteResult: EmptyResult<DataError.Network> = Result.Success(Unit)
        val calls = mutableListOf<String>()

        /** Runs while a request is in flight, to simulate the user changing data meanwhile. */
        var duringRequest: suspend () -> Unit = {}

        override suspend fun getLastPageUsers() = lastPageResult.also { calls += "GET last page" }

        override suspend fun createUser(name: String, email: String) = createResult.also {
            calls += "POST $name"
            duringRequest()
        }

        override suspend fun updateUser(id: Long, name: String, email: String) = updateResult.also {
            calls += "PATCH $id $name"
            duringRequest()
        }

        override suspend fun findUserByEmail(email: String) = findResult.also { calls += "GET email $email" }

        override suspend fun deleteUser(id: Long) = deleteResult.also {
            calls += "DELETE $id"
            duringRequest()
        }
    }

    /** No real transaction: the fakes never suspend inside one, so its writes already land together. */
    private class FakeTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
    }

    private class FakeUserDao : UserDao {
        val rows = MutableStateFlow<Map<Long, UserEntity>>(emptyMap())

        override suspend fun insertAll(users: List<UserEntity>) {
            rows.value += users.associateBy { it.id }
        }

        override suspend fun updateDetails(id: Long, name: String, email: String) {
            rows.value[id]?.let { rows.value += id to it.copy(name = name, email = email) }
        }

        override suspend fun setLastSyncedAt(id: Long, syncedAt: Long) {
            rows.value[id]?.let { rows.value += id to it.copy(lastSyncedAt = syncedAt) }
        }

        override suspend fun deleteById(id: Long) {
            rows.value -= id
        }

        override suspend fun deleteAll() {
            rows.value = emptyMap()
        }

        override suspend fun replaceAll(users: List<UserEntity>) {
            rows.value = users.associateBy { it.id }
        }

        override fun getAll(): Flow<List<UserEntity>> = rows.map { it.values.sortedByDescending(UserEntity::id) }
    }

    private class FakeCreatedUserDao : CreatedUserDao {
        val rows = MutableStateFlow<Map<Long, CreatedUserEntity>>(emptyMap())
        private var nextId = 1L

        override suspend fun upsert(user: CreatedUserEntity): Long {
            val row = if (user.localId == 0L) user.copy(localId = nextId++) else user
            rows.value += row.localId to row
            return row.localId
        }

        override fun getAll(): Flow<List<CreatedUserEntity>> = rows.map {
            it.values.sortedWith(
                compareByDescending<CreatedUserEntity> { u ->
                    u.createdAt
                }.thenByDescending { u -> u.localId }
            )
        }

        override suspend fun getUnsynced() = rows.value.values.filter { it.remoteId == null }
            .sortedWith(compareBy<CreatedUserEntity> { it.createdAt }.thenBy { it.localId })

        override suspend fun getByLocalId(localId: Long) = rows.value[localId]

        override suspend fun markSent(localId: Long) {
            rows.value[localId]?.let { rows.value += localId to it.copy(wasSent = true) }
        }

        override suspend fun setRemoteId(localId: Long, remoteId: Long, syncedAt: Long) {
            rows.value[localId]?.let { rows.value += localId to it.copy(remoteId = remoteId, lastSyncedAt = syncedAt) }
        }

        override suspend fun setLastSyncedAt(remoteId: Long, syncedAt: Long) {
            rows.value = rows.value.mapValues { (_, row) ->
                if (row.remoteId == remoteId) row.copy(lastSyncedAt = syncedAt) else row
            }
        }

        override suspend fun updateDetails(localId: Long, name: String, email: String) {
            rows.value[localId]?.let { rows.value += localId to it.copy(name = name, email = email) }
        }

        override suspend fun deleteByLocalId(localId: Long) {
            rows.value -= localId
        }
    }

    /** Mirrors the real query: an unsynced created user counts as pending, as do the edit and delete queues. */
    private class FakeUserPendingSyncDao(private val createdRows: Flow<Map<Long, CreatedUserEntity>>) :
        UserPendingSyncDao {
        val updates = MutableStateFlow<Map<Long, UpdateUserPendingSyncEntity>>(emptyMap())
        val deletes = MutableStateFlow<Map<Long, DeleteUserPendingSyncEntity>>(emptyMap())

        override suspend fun upsertUpdatePendingSync(item: UpdateUserPendingSyncEntity) {
            updates.value += item.userId to item
        }

        override suspend fun getUpdatePendingSyncs() = updates.value.values.toList()

        override suspend fun getUpdatePendingSync(userId: Long) = updates.value[userId]

        override fun observeUpdatePendingSyncs(): Flow<List<UpdateUserPendingSyncEntity>> =
            updates.map { it.values.toList() }

        override suspend fun deleteUpdatePendingSync(userId: Long) {
            updates.value -= userId
        }

        override suspend fun deleteUpdatePendingSyncIfUnchanged(userId: Long, updatedAt: Long) {
            if (updates.value[userId]?.updatedAt == updatedAt) updates.value -= userId
        }

        override suspend fun upsertDeletePendingSync(item: DeleteUserPendingSyncEntity) {
            deletes.value += item.userId to item
        }

        override suspend fun getDeletePendingSyncs() = deletes.value.values.toList()

        override suspend fun getDeletePendingSync(userId: Long) = deletes.value[userId]

        override fun observeDeletePendingSyncs(): Flow<List<DeleteUserPendingSyncEntity>> =
            deletes.map { it.values.toList() }

        override suspend fun deleteDeletePendingSync(userId: Long) {
            deletes.value -= userId
        }

        override fun observeHasPendingSyncs(): Flow<Boolean> = combine(createdRows, updates, deletes) { c, u, d ->
            c.values.any { it.remoteId == null } || u.isNotEmpty() || d.isNotEmpty()
        }
    }

    private class FakeUserSyncScheduler : UserSyncScheduler {
        var scheduled = 0
        val changeSyncs = mutableListOf<PendingUserChange>()

        override fun scheduleSync() {
            scheduled++
        }

        override fun scheduleChangeSync(change: PendingUserChange) {
            changeSyncs += change
        }

        override fun cancelSync() = Unit
    }

    private var now = Instant.fromEpochMilliseconds(1_000)
    private val api = FakeUserFeedApi()
    private val userDao = FakeUserDao()
    private val createdUserDao = FakeCreatedUserDao()
    private val pendingSyncDao = FakeUserPendingSyncDao(createdUserDao.rows)
    private val scheduler = FakeUserSyncScheduler()
    private val repository = OfflineFirstUserFeedRepository(
        userFeedApi = api,
        userDao = userDao,
        createdUserDao = createdUserDao,
        pendingSyncDao = pendingSyncDao,
        transactionRunner = FakeTransactionRunner(),
        userSyncScheduler = scheduler,
        applicationScope = ApplicationScope(TestScope(UnconfinedTestDispatcher())),
        clock = object : Clock {
            override fun now() = this@OfflineFirstUserFeedRepositoryTest.now
        }
    )

    /** Fetched before [now], so a fresh sync stamp is distinguishable from the cached one. */
    private val ada = UserEntity(1, "Ada", "ada@x.io", lastSyncedAt = 500)
    private val alan = UserEntity(2, "Alan", "alan@x.io", lastSyncedAt = 500)
    private val fetchedAt = Instant.fromEpochMilliseconds(500)

    private suspend fun feed() = repository.observeUsers().first()

    private suspend fun hasPendingSyncs() = repository.observeHasPendingSyncs().first()

    // observeUsers

    @Test
    fun `created users come first and cached users follow`() = runTest {
        userDao.insertAll(listOf(ada, alan))
        createdUserDao.upsert(CreatedUserEntity(name = "Grace", email = "grace@x.io", createdAt = 1))

        assertThat(feed()).containsExactly(
            FeedUser(FeedUserId.Created(1), "Grace", "grace@x.io", lastSyncedAt = null),
            FeedUser(FeedUserId.Remote(2), "Alan", "alan@x.io", fetchedAt),
            FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", fetchedAt)
        )
    }

    @Test
    fun `pending deletes are hidden and pending edits are overlaid on cached users as unsynced`() = runTest {
        userDao.insertAll(listOf(ada, alan))
        pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(2))
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 0))

        assertThat(feed()).containsExactly(FeedUser(FeedUserId.Remote(1), "Ada L", "ada@l.io", lastSyncedAt = null))
    }

    @Test
    fun `a posted created user shows its sync time until an edit of it is queued`() = runTest {
        createdUserDao.upsert(
            CreatedUserEntity(remoteId = 100, name = "Grace", email = "grace@x.io", createdAt = 1, lastSyncedAt = 500)
        )
        assertThat(feed().single().lastSyncedAt).isEqualTo(fetchedAt)

        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(100, "Grace H", "grace@x.io", updatedAt = 0))

        assertThat(feed().single().lastSyncedAt).isEqualTo(null)
    }

    @Test
    fun `a synced created user that is also on the fetched page is shown once`() = runTest {
        createdUserDao.upsert(CreatedUserEntity(remoteId = 2, name = "Alan", email = "alan@x.io", createdAt = 1))
        userDao.insertAll(listOf(ada, alan))

        assertThat(feed().map { it.id }).containsExactly(FeedUserId.Created(1), FeedUserId.Remote(1))
    }

    @Test
    fun `observers see each create update and delete`() = runTest {
        repository.observeUsers().test {
            assertThat(awaitItem()).isEmpty()

            repository.createUser("Grace", "grace@x.io")
            assertThat(expectMostRecentItem())
                .containsExactly(FeedUser(FeedUserId.Created(1), "Grace", "grace@x.io", now))

            repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@x.io")
            assertThat(expectMostRecentItem())
                .containsExactly(FeedUser(FeedUserId.Created(1), "Grace H", "grace@x.io", now))

            repository.deleteUser(FeedUserId.Created(1))
            assertThat(expectMostRecentItem()).isEmpty()
        }
    }

    @Test
    fun `a saved change is shown as unsynced before it reaches GoRest`() = runTest {
        userDao.insertAll(listOf(ada))
        val shownDuringRequest = mutableListOf<List<FeedUser>>()
        api.duringRequest = { shownDuringRequest += feed() }

        repository.createUser("Grace", "grace@x.io")
        repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io")

        assertThat(shownDuringRequest).containsExactly(
            listOf(
                FeedUser(FeedUserId.Created(1), "Grace", "grace@x.io", null),
                FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", fetchedAt)
            ),
            listOf(
                FeedUser(FeedUserId.Created(1), "Grace", "grace@x.io", now),
                FeedUser(FeedUserId.Remote(1), "Ada L", "ada@l.io", null)
            )
        )
    }

    // loadUsers

    @Test
    fun `load sends queued changes before fetching the last page`() = runTest {
        userDao.insertAll(listOf(ada))
        pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(1))

        repository.loadUsers()

        assertThat(api.calls).containsExactly("DELETE 1", "GET last page")
    }

    @Test
    fun `load replaces the cached page and keeps created users on top`() = runTest {
        userDao.insertAll(listOf(ada))
        createdUserDao.upsert(CreatedUserEntity(name = "Grace", email = "grace@x.io", createdAt = 1))
        api.lastPageResult = Result.Success(listOf(UserDto(2, "Alan", "alan@x.io")))

        val result = repository.loadUsers()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(feed().map { it.id }).containsExactly(FeedUserId.Created(1), FeedUserId.Remote(2))
    }

    @Test
    fun `load offline reports the error and keeps the cache and queued changes`() = runTest {
        userDao.insertAll(listOf(ada, alan))
        createdUserDao.upsert(CreatedUserEntity(name = "Grace", email = "grace@x.io", createdAt = 1))
        api.createResult = Result.Error(DataError.Network.NO_INTERNET)
        api.lastPageResult = Result.Error(DataError.Network.NO_INTERNET)

        val result = repository.loadUsers()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(feed().map { it.id })
            .containsExactly(FeedUserId.Created(1), FeedUserId.Remote(2), FeedUserId.Remote(1))
        assertThat(createdUserDao.rows.value.getValue(1).remoteId).isEqualTo(null)
        assertThat(hasPendingSyncs()).isTrue()
    }

    @Test
    fun `load stamps every fetched user with the fetch time`() = runTest {
        api.lastPageResult = Result.Success(listOf(UserDto(1, "Ada", "ada@x.io"), UserDto(2, "Alan", "alan@x.io")))

        repository.loadUsers()

        assertThat(userDao.rows.value.values.map { it.lastSyncedAt }).containsExactly(1_000L, 1_000L)
    }

    // loadUsersIfNotCached

    @Test
    fun `first load fetches the last page when nothing is cached`() = runTest {
        api.lastPageResult = Result.Success(listOf(UserDto(1, "Ada", "ada@x.io")))

        val result = repository.loadUsersIfNotCached()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(feed().map { it.id }).containsExactly(FeedUserId.Remote(1))
    }

    @Test
    fun `first load fetches even when only created users are stored`() = runTest {
        // Synced, so the load has nothing to send first.
        createdUserDao.upsert(CreatedUserEntity(remoteId = 100, name = "Grace", email = "grace@x.io", createdAt = 1))

        repository.loadUsersIfNotCached()

        assertThat(api.calls).containsExactly("GET last page")
    }

    @Test
    fun `first load does nothing when a page is cached`() = runTest {
        userDao.insertAll(listOf(ada))

        val result = repository.loadUsersIfNotCached()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).isEmpty()
    }

    @Test
    fun `first load offline reports the error`() = runTest {
        api.lastPageResult = Result.Error(DataError.Network.NO_INTERNET)

        val result = repository.loadUsersIfNotCached()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(feed()).isEmpty()
    }

    // createUser

    @Test
    fun `create stores the user then posts it and records the remote id`() = runTest {
        val result = repository.createUser("Grace", "grace@x.io")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(createdUserDao.rows.value.values.toList()).containsExactly(
            CreatedUserEntity(
                localId = 1,
                remoteId = 100,
                name = "Grace",
                email = "grace@x.io",
                createdAt = 1_000,
                lastSyncedAt = 1_000,
                wasSent = true
            )
        )
        assertThat(hasPendingSyncs()).isFalse()
    }

    @Test
    fun `failed create keeps the user pending without a remote id`() = runTest {
        api.createResult = Result.Error(DataError.Network.NO_INTERNET)

        val result = repository.createUser("Grace", "grace@x.io")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(createdUserDao.rows.value.values.toList()).containsExactly(
            CreatedUserEntity(
                localId = 1,
                remoteId = null,
                name = "Grace",
                email = "grace@x.io",
                createdAt = 1_000,
                wasSent = true
            )
        )
        assertThat(hasPendingSyncs()).isTrue()
    }

    @Test
    fun `a create is pending and a sync scheduled before it is posted so it survives the app dying mid-request`() =
        runTest {
            var pendingDuringPost: Boolean? = null
            var scheduledDuringPost: Int? = null
            api.duringRequest = {
                pendingDuringPost = hasPendingSyncs()
                scheduledDuringPost = scheduler.scheduled
            }

            repository.createUser("Grace", "grace@x.io")

            assertThat(pendingDuringPost).isEqualTo(true)
            assertThat(scheduledDuringPost).isEqualTo(1)
        }

    @Test
    fun `a created user edited while its own post is in flight has the edit queued and a sync scheduled`() = runTest {
        api.duringRequest = {
            api.duringRequest = {}
            repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@h.io")
        }

        repository.createUser("Grace", "grace@x.io")

        assertThat(api.calls).containsExactly("POST Grace")
        assertThat(pendingSyncDao.updates.value.values.toList())
            .containsExactly(UpdateUserPendingSyncEntity(100, "Grace H", "grace@h.io", updatedAt = 1_000))
        assertThat(scheduler.scheduled).isEqualTo(2)
        assertThat(scheduler.changeSyncs).containsExactly(PendingUserChange.Update(100))
    }

    @Test
    fun `a create keeps going when the caller is cancelled mid-request`() = runTest {
        val response = CompletableDeferred<Unit>()
        api.duringRequest = { response.await() }

        val caller = launch { repository.createUser("Grace", "grace@x.io") }
        runCurrent()
        caller.cancel()
        response.complete(Unit)

        assertThat(createdUserDao.rows.value.getValue(1).remoteId).isEqualTo(100L)
    }

    @Test
    fun `create rejected by GoRest removes the user without a lookup`() = runTest {
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)

        val result = repository.createUser("Grace", "grace@x.io")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNPROCESSABLE_ENTITY))
        // A first POST can't have been lost, so the email really is taken by someone else.
        assertThat(api.calls).containsExactly("POST Grace")
        assertThat(createdUserDao.rows.value).isEmpty()
        assertThat(hasPendingSyncs()).isFalse()
    }

    // updateUser

    @Test
    fun `updating a cached user writes it locally patches it and stamps the sync time`() = runTest {
        userDao.insertAll(listOf(ada))

        val result = repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(userDao.rows.value.values.toList()).containsExactly(UserEntity(1, "Ada L", "ada@l.io", 1_000))
        assertThat(api.calls).containsExactly("PATCH 1 Ada L")
        assertThat(pendingSyncDao.updates.value).isEmpty()
    }

    @Test
    fun `successful update drops an older queued edit`() = runTest {
        userDao.insertAll(listOf(ada))
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Old", "old@x.io", updatedAt = 0))

        repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io")

        assertThat(pendingSyncDao.updates.value).isEmpty()
    }

    @Test
    fun `failed update keeps the local edit and queues it`() = runTest {
        userDao.insertAll(listOf(ada))
        api.updateResult = Result.Error(DataError.Network.REQUEST_TIMEOUT)

        val result = repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.REQUEST_TIMEOUT))
        assertThat(userDao.rows.value.values.toList()).containsExactly(UserEntity(1, "Ada L", "ada@l.io", 500))
        assertThat(pendingSyncDao.updates.value.values.toList()).containsExactly(
            UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 1_000)
        )
    }

    @Test
    fun `an edit is queued and a sync scheduled before it is patched so it survives the app dying mid-request`() =
        runTest {
            userDao.insertAll(listOf(ada))
            var queuedDuringPatch: List<UpdateUserPendingSyncEntity>? = null
            var scheduledDuringPatch: Int? = null
            api.duringRequest = {
                queuedDuringPatch = pendingSyncDao.updates.value.values.toList()
                scheduledDuringPatch = scheduler.scheduled
            }

            repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io")

            assertThat(queuedDuringPatch)
                .isEqualTo(listOf(UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 1_000)))
            assertThat(scheduledDuringPatch).isEqualTo(1)
        }

    @Test
    fun `an edit saved while an older one is being patched is sent after it and wins`() = runTest {
        userDao.insertAll(listOf(ada))
        api.updateResult = Result.Error(DataError.Network.NO_INTERNET)
        repository.updateUser(FeedUserId.Remote(1), "Ada A", "ada@x.io")
        api.updateResult = Result.Success(UserDto(1, "", ""))
        api.calls.clear()
        var newerSave: Job? = null
        api.duringRequest = {
            api.duringRequest = {}
            now = Instant.fromEpochMilliseconds(2_000)
            // Undispatched, so the save is written now, while the request is in flight.
            newerSave = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.updateUser(FeedUserId.Remote(1), "Ada B", "ada@x.io")
            }
        }

        repository.syncPendingChanges()
        newerSave?.join()

        assertThat(api.calls).containsExactly("PATCH 1 Ada A", "PATCH 1 Ada B")
        assertThat(userDao.rows.value.getValue(1).name).isEqualTo("Ada B")
        assertThat(pendingSyncDao.updates.value).isEmpty()
    }

    @Test
    fun `an edit a running sync already sent is not sent again`() = runTest {
        userDao.insertAll(listOf(ada))
        queueOfflineCreate("Grace")
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada A", "ada@x.io", updatedAt = 5))
        var newerSave: Job? = null
        // While the sync is busy with the create, the queued edit is replaced by a newer one.
        api.duringRequest = {
            api.duringRequest = {}
            // Undispatched, so the save is written now, while the request is in flight.
            newerSave = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.updateUser(FeedUserId.Remote(1), "Ada B", "ada@x.io")
            }
        }

        repository.syncPendingChanges()
        newerSave?.join()

        assertThat(api.calls).containsExactly("POST Grace", "PATCH 1 Ada B")
        assertThat(pendingSyncDao.updates.value).isEmpty()
    }

    @Test
    fun `updating an unsynced created user only edits the row`() = runTest {
        queueOfflineCreate("Grace")
        val scheduledBefore = scheduler.scheduled

        val result = repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@h.io")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(createdUserDao.rows.value.getValue(1).name).isEqualTo("Grace H")
        assertThat(api.calls).isEmpty()
        assertThat(pendingSyncDao.updates.value).isEmpty()
        assertThat(scheduler.scheduled).isEqualTo(scheduledBefore)
    }

    @Test
    fun `a patched created user is stamped with the sync time`() = runTest {
        repository.createUser("Grace", "grace@x.io")
        now = Instant.fromEpochMilliseconds(2_000)

        repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@h.io")

        assertThat(createdUserDao.rows.value.getValue(1).lastSyncedAt).isEqualTo(2_000L)
    }

    @Test
    fun `updating a synced created user edits the row and patches its remote id`() = runTest {
        repository.createUser("Grace", "grace@x.io")
        api.updateResult = Result.Error(DataError.Network.NO_INTERNET)

        repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@h.io")

        assertThat(createdUserDao.rows.value.getValue(1).name).isEqualTo("Grace H")
        assertThat(api.calls).containsExactly("POST Grace", "PATCH 100 Grace H")
        assertThat(pendingSyncDao.updates.value.keys.toList()).containsExactly(100L)
    }

    // deleteUser

    @Test
    fun `deleting a cached user removes it and sends the delete`() = runTest {
        userDao.insertAll(listOf(ada))

        val result = repository.deleteUser(FeedUserId.Remote(1))

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(userDao.rows.value).isEmpty()
        assertThat(api.calls).containsExactly("DELETE 1")
        assertThat(pendingSyncDao.deletes.value).isEmpty()
    }

    @Test
    fun `a delete is queued and a sync scheduled before it is sent so it survives the app dying mid-request`() =
        runTest {
            userDao.insertAll(listOf(ada))
            var queuedDuringDelete: List<DeleteUserPendingSyncEntity>? = null
            var scheduledDuringDelete: Int? = null
            api.duringRequest = {
                queuedDuringDelete = pendingSyncDao.deletes.value.values.toList()
                scheduledDuringDelete = scheduler.scheduled
            }

            repository.deleteUser(FeedUserId.Remote(1))

            assertThat(queuedDuringDelete).isEqualTo(listOf(DeleteUserPendingSyncEntity(1)))
            assertThat(scheduledDuringDelete).isEqualTo(1)
        }

    @Test
    fun `failed delete queues a pending delete and drops any queued edit`() = runTest {
        userDao.insertAll(listOf(ada))
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Old", "old@x.io", updatedAt = 0))
        api.deleteResult = Result.Error(DataError.Network.NO_INTERNET)

        val result = repository.deleteUser(FeedUserId.Remote(1))

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(userDao.rows.value).isEmpty()
        assertThat(pendingSyncDao.deletes.value.values.toList()).containsExactly(DeleteUserPendingSyncEntity(1))
        assertThat(pendingSyncDao.updates.value).isEmpty()
    }

    @Test
    fun `deleting an unsynced created user drops it without a request`() = runTest {
        queueOfflineCreate("Grace")
        val scheduledBefore = scheduler.scheduled

        val result = repository.deleteUser(FeedUserId.Created(1))

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(createdUserDao.rows.value).isEmpty()
        assertThat(hasPendingSyncs()).isFalse()
        assertThat(api.calls).isEmpty()
        assertThat(scheduler.scheduled).isEqualTo(scheduledBefore)
    }

    @Test
    fun `deleting a synced created user removes the row and deletes its remote id`() = runTest {
        repository.createUser("Grace", "grace@x.io")
        api.deleteResult = Result.Error(DataError.Network.NO_INTERNET)

        repository.deleteUser(FeedUserId.Created(1))

        assertThat(createdUserDao.rows.value).isEmpty()
        assertThat(api.calls).containsExactly("POST Grace", "DELETE 100")
        assertThat(pendingSyncDao.deletes.value.values.toList()).containsExactly(DeleteUserPendingSyncEntity(100))
    }

    // syncPendingChanges

    private suspend fun queueOfflineCreate(name: String) {
        api.createResult = Result.Error(DataError.Network.NO_INTERNET)
        repository.createUser(name, "${name.lowercase()}@x.io")
        api.createResult = Result.Success(UserDto(100, "", ""))
        api.calls.clear()
    }

    @Test
    fun `sync with nothing pending succeeds without requests`() = runTest {
        assertThat(repository.syncPendingChanges()).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).isEmpty()
    }

    @Test
    fun `sync posts a pending create records its remote id and clears it`() = runTest {
        queueOfflineCreate("Grace")

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).containsExactly("POST Grace")
        assertThat(createdUserDao.rows.value.getValue(1).remoteId).isEqualTo(100L)
        assertThat(createdUserDao.rows.value.getValue(1).lastSyncedAt).isEqualTo(1_000L)
        assertThat(hasPendingSyncs()).isFalse()
    }

    @Test
    fun `sync patches a pending edit writes it to the cache and clears it`() = runTest {
        userDao.insertAll(listOf(ada))
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 5))

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).containsExactly("PATCH 1 Ada L")
        assertThat(userDao.rows.value.values.toList()).containsExactly(UserEntity(1, "Ada L", "ada@l.io", 1_000))
        assertThat(pendingSyncDao.updates.value).isEmpty()
    }

    @Test
    fun `sync sends a pending delete and clears it`() = runTest {
        pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(2))

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).containsExactly("DELETE 2")
        assertThat(pendingSyncDao.deletes.value).isEmpty()
    }

    @Test
    fun `overlapping syncs send each pending change once`() = runTest {
        queueOfflineCreate("Grace")
        api.duringRequest = { yield() }

        listOf(launch { repository.syncPendingChanges() }, launch { repository.syncPendingChanges() }).joinAll()

        assertThat(api.calls).containsExactly("POST Grace")
    }

    @Test
    fun `failed items stay queued while the others still sync`() = runTest {
        queueOfflineCreate("Grace")
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 5))
        pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(2))
        api.createResult = Result.Error(DataError.Network.SERVER_ERROR)
        api.updateResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
        assertThat(api.calls).containsExactly("POST Grace", "PATCH 1 Ada L", "DELETE 2")
        assertThat(createdUserDao.rows.value.getValue(1).remoteId).isEqualTo(null)
        assertThat(pendingSyncDao.updates.value.keys.toList()).containsExactly(1L)
        assertThat(pendingSyncDao.deletes.value).isEmpty()
    }

    @Test
    fun `not found clears a pending delete since the user is already gone`() = runTest {
        pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(2))
        api.deleteResult = Result.Error(DataError.Network.NOT_FOUND)

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(pendingSyncDao.deletes.value).isEmpty()
    }

    @Test
    fun `not found clears a pending edit since the user is gone`() = runTest {
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 5))
        api.updateResult = Result.Error(DataError.Network.NOT_FOUND)

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(pendingSyncDao.updates.value).isEmpty()
    }

    @Test
    fun `an edit queued while its patch is in flight stays queued`() = runTest {
        userDao.insertAll(listOf(ada))
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 5))
        api.duringRequest = {
            api.duringRequest = {}
            pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada M", "ada@m.io", updatedAt = 6))
        }

        repository.syncPendingChanges()

        assertThat(pendingSyncDao.updates.value.values.toList()).containsExactly(
            UpdateUserPendingSyncEntity(1, "Ada M", "ada@m.io", updatedAt = 6)
        )
    }

    @Test
    fun `a created user edited while its post is in flight has the edit patched`() = runTest {
        queueOfflineCreate("Grace")
        api.duringRequest = {
            api.duringRequest = {}
            repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@h.io")
        }

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).containsExactly("POST Grace", "PATCH 100 Grace H")
        assertThat(hasPendingSyncs()).isFalse()
    }

    @Test
    fun `a created user deleted while its post is in flight is deleted on the server`() = runTest {
        queueOfflineCreate("Grace")
        api.duringRequest = {
            api.duringRequest = {}
            repository.deleteUser(FeedUserId.Created(1))
        }

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).containsExactly("POST Grace", "DELETE 100")
        assertThat(createdUserDao.rows.value).isEmpty()
        assertThat(hasPendingSyncs()).isFalse()
    }

    @Test
    fun `a resent create refused for its email adopts the user its lost 201 created`() = runTest {
        queueOfflineCreate("Grace")
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)
        api.findResult = Result.Success(UserDto(100, "Grace", "grace@x.io"))

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).containsExactly("POST Grace", "GET email grace@x.io")
        assertThat(createdUserDao.rows.value.getValue(1).remoteId).isEqualTo(100L)
    }

    @Test
    fun `a resent create refused because someone else has the email is rejected`() = runTest {
        queueOfflineCreate("Grace")
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)
        api.findResult = Result.Success(UserDto(55, "Someone Else", "grace@x.io"))

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNPROCESSABLE_ENTITY))
        assertThat(createdUserDao.rows.value).isEmpty()
    }

    @Test
    fun `a resent create stays queued when the lookup fails`() = runTest {
        queueOfflineCreate("Grace")
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)
        api.findResult = Result.Error(DataError.Network.NO_INTERNET)

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(createdUserDao.rows.value.getValue(1).remoteId).isEqualTo(null)
        assertThat(hasPendingSyncs()).isTrue()
    }

    @Test
    fun `a pending create rejected by GoRest is removed with its user`() = runTest {
        queueOfflineCreate("Grace")
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNPROCESSABLE_ENTITY))
        assertThat(createdUserDao.rows.value).isEmpty()
    }

    // observeHasPendingSyncs

    @Test
    fun `has pending syncs follows the queues`() = runTest {
        repository.observeHasPendingSyncs().test {
            assertThat(awaitItem()).isFalse()

            pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(2))
            assertThat(awaitItem()).isTrue()

            repository.syncPendingChanges()
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `has pending syncs counts a created user until GoRest accepts it`() = runTest {
        queueOfflineCreate("Grace")
        assertThat(hasPendingSyncs()).isTrue()

        repository.syncPendingChanges()

        assertThat(hasPendingSyncs()).isFalse()
    }

    // Per-change retries

    @Test
    fun `a save whose request fails gets a retry of its own`() = runTest {
        userDao.insertAll(listOf(ada, alan))
        api.createResult = Result.Error(DataError.Network.NO_INTERNET)
        api.updateResult = Result.Error(DataError.Network.SERVER_ERROR)
        api.deleteResult = Result.Error(DataError.Network.REQUEST_TIMEOUT)

        repository.createUser("Grace", "grace@x.io")
        repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io")
        repository.deleteUser(FeedUserId.Remote(2))

        assertThat(scheduler.changeSyncs).containsExactly(
            PendingUserChange.Create(1),
            PendingUserChange.Update(1),
            PendingUserChange.Delete(2)
        )
    }

    @Test
    fun `a save that succeeds or is rejected for good gets no retry`() = runTest {
        userDao.insertAll(listOf(ada))
        repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io")
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)
        repository.createUser("Grace", "grace@x.io")
        api.deleteResult = Result.Error(DataError.Network.NOT_FOUND)
        repository.deleteUser(FeedUserId.Remote(1))

        assertThat(scheduler.changeSyncs).isEmpty()
    }

    @Test
    fun `a change sync sends just that change`() = runTest {
        queueOfflineCreate("Grace")
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 5))
        pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(2))

        val result = repository.syncChange(PendingUserChange.Delete(2))

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).containsExactly("DELETE 2")
        assertThat(pendingSyncDao.deletes.value).isEmpty()
        assertThat(pendingSyncDao.updates.value.keys.toList()).containsExactly(1L)
        assertThat(createdUserDao.rows.value.getValue(1).remoteId).isEqualTo(null)
    }

    @Test
    fun `a change sync reports an error only while the change stays pending`() = runTest {
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 5))
        api.updateResult = Result.Error(DataError.Network.SERVER_ERROR)

        assertThat(repository.syncChange(PendingUserChange.Update(1)))
            .isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))

        api.updateResult = Result.Error(DataError.Network.NOT_FOUND)

        assertThat(repository.syncChange(PendingUserChange.Update(1))).isEqualTo(Result.Success(Unit))
        assertThat(pendingSyncDao.updates.value).isEmpty()
    }

    @Test
    fun `a change sync of a create GoRest rejects succeeds since retrying can't help`() = runTest {
        queueOfflineCreate("Grace")
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)

        val result = repository.syncChange(PendingUserChange.Create(1))

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(createdUserDao.rows.value).isEmpty()
    }

    @Test
    fun `a change sync of something already sent makes no request`() = runTest {
        val result = repository.syncChange(PendingUserChange.Update(1))

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(api.calls).isEmpty()
    }

    // Per-user locking

    @Test
    fun `a change to one user is sent while another user's request is still in flight`() = runTest {
        userDao.insertAll(listOf(ada, alan))
        val patchResponse = CompletableDeferred<Unit>()
        api.duringRequest = { if (api.calls.last().startsWith("PATCH")) patchResponse.await() }

        val slowSave = launch { repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io") }
        runCurrent()
        repository.deleteUser(FeedUserId.Remote(2))

        // The DELETE went out and finished without waiting for the PATCH.
        assertThat(api.calls).containsExactly("PATCH 1 Ada L", "DELETE 2")
        assertThat(pendingSyncDao.deletes.value).isEmpty()
        patchResponse.complete(Unit)
        slowSave.join()
    }

    @Test
    fun `changes to the same user wait for each other and go out in order`() = runTest {
        userDao.insertAll(listOf(ada))
        val patchResponse = CompletableDeferred<Unit>()
        api.duringRequest = { if (api.calls.last().startsWith("PATCH")) patchResponse.await() }

        val slowSave = launch { repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io") }
        runCurrent()
        val delete = launch { repository.deleteUser(FeedUserId.Remote(1)) }
        runCurrent()

        assertThat(api.calls).containsExactly("PATCH 1 Ada L")
        patchResponse.complete(Unit)
        listOf(slowSave, delete).joinAll()
        assertThat(api.calls).containsExactly("PATCH 1 Ada L", "DELETE 1")
    }
}
