package com.androidlearning.sliide_challenge.userfeed.data

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.androidlearning.sliide_challenge.core.data.user.UserDto
import com.androidlearning.sliide_challenge.core.database.user.CreateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserDao
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserEntity
import com.androidlearning.sliide_challenge.core.database.user.DeleteUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.RejectedCreationDao
import com.androidlearning.sliide_challenge.core.database.user.RejectedCreationEntity
import com.androidlearning.sliide_challenge.core.database.user.UpdateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UserDao
import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.core.database.user.UserPendingSyncDao
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.RejectedCreation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

class OfflineFirstUserFeedRepositoryTest {

    private class FakeUserFeedApi : UserFeedApi {
        var createResult: Result<UserDto, DataError.Network> = Result.Success(UserDto(100, "", ""))
        var updateResult: Result<UserDto, DataError.Network> = Result.Success(UserDto(1, "", ""))
        var deleteResult: EmptyResult<DataError.Network> = Result.Success(Unit)
        val calls = mutableListOf<String>()

        /** Runs while a request is in flight, to simulate the user changing data meanwhile. */
        var duringRequest: suspend () -> Unit = {}

        override suspend fun createUser(name: String, email: String) =
            createResult.also { calls += "POST $name"; duringRequest() }

        override suspend fun updateUser(id: Long, name: String, email: String) =
            updateResult.also { calls += "PATCH $id $name"; duringRequest() }

        override suspend fun deleteUser(id: Long) = deleteResult.also { calls += "DELETE $id"; duringRequest() }
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

        override fun getAll(): Flow<List<CreatedUserEntity>> =
            rows.map { it.values.sortedWith(compareByDescending<CreatedUserEntity> { u -> u.createdAt }.thenByDescending { u -> u.localId }) }

        override suspend fun getByLocalId(localId: Long) = rows.value[localId]

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

    private class FakeUserPendingSyncDao : UserPendingSyncDao {
        val creates = MutableStateFlow<Map<Long, CreateUserPendingSyncEntity>>(emptyMap())
        val updates = MutableStateFlow<Map<Long, UpdateUserPendingSyncEntity>>(emptyMap())
        val deletes = MutableStateFlow<Map<Long, DeleteUserPendingSyncEntity>>(emptyMap())

        override suspend fun upsertCreatePendingSync(item: CreateUserPendingSyncEntity) {
            creates.value += item.localId to item
        }

        override suspend fun getCreatePendingSyncs() = creates.value.values.toList()

        override suspend fun deleteCreatePendingSync(localId: Long) {
            creates.value -= localId
        }

        override suspend fun upsertUpdatePendingSync(item: UpdateUserPendingSyncEntity) {
            updates.value += item.userId to item
        }

        override suspend fun getUpdatePendingSyncs() = updates.value.values.toList()

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

        override fun observeDeletePendingSyncs(): Flow<List<DeleteUserPendingSyncEntity>> =
            deletes.map { it.values.toList() }

        override suspend fun deleteDeletePendingSync(userId: Long) {
            deletes.value -= userId
        }

        override fun observeHasPendingSyncs(): Flow<Boolean> =
            combine(creates, updates, deletes) { c, u, d -> c.isNotEmpty() || u.isNotEmpty() || d.isNotEmpty() }
    }

    private class FakeRejectedCreationDao : RejectedCreationDao {
        val rows = MutableStateFlow<List<RejectedCreationEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insert(item: RejectedCreationEntity) {
            rows.value += item.copy(id = nextId++)
        }

        override fun observeAll(): Flow<List<RejectedCreationEntity>> = rows

        override suspend fun deleteById(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }

    private var now = Instant.fromEpochMilliseconds(1_000)
    private val api = FakeUserFeedApi()
    private val userDao = FakeUserDao()
    private val createdUserDao = FakeCreatedUserDao()
    private val pendingSyncDao = FakeUserPendingSyncDao()
    private val rejectedCreationDao = FakeRejectedCreationDao()
    private val repository = OfflineFirstUserFeedRepository(
        userFeedApi = api,
        userDao = userDao,
        createdUserDao = createdUserDao,
        pendingSyncDao = pendingSyncDao,
        rejectedCreationDao = rejectedCreationDao,
        clock = object : Clock {
            override fun now() = this@OfflineFirstUserFeedRepositoryTest.now
        },
    )

    /** Fetched before [now], so a fresh sync stamp is distinguishable from the cached one. */
    private val ada = UserEntity(1, "Ada", "ada@x.io", lastSyncedAt = 500)
    private val alan = UserEntity(2, "Alan", "alan@x.io", lastSyncedAt = 500)
    private val fetchedAt = Instant.fromEpochMilliseconds(500)

    private suspend fun feed() = repository.observeUsers().first()

    // observeUsers

    @Test
    fun `created users come first and cached users follow`() = runTest {
        userDao.insertAll(listOf(ada, alan))
        createdUserDao.upsert(CreatedUserEntity(name = "Grace", email = "grace@x.io", createdAt = 1))

        assertThat(feed()).containsExactly(
            FeedUser(FeedUserId.Created(1), "Grace", "grace@x.io", lastSyncedAt = null),
            FeedUser(FeedUserId.Remote(2), "Alan", "alan@x.io", fetchedAt),
            FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", fetchedAt),
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
            CreatedUserEntity(remoteId = 100, name = "Grace", email = "grace@x.io", createdAt = 1, lastSyncedAt = 500),
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
            assertThat(awaitItem()).containsExactly(FeedUser(FeedUserId.Created(1), "Grace", "grace@x.io", null))
            assertThat(awaitItem()).containsExactly(FeedUser(FeedUserId.Created(1), "Grace", "grace@x.io", now))

            repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@x.io")
            assertThat(awaitItem()).containsExactly(FeedUser(FeedUserId.Created(1), "Grace H", "grace@x.io", now))

            repository.deleteUser(FeedUserId.Created(1))
            assertThat(awaitItem()).isEmpty()
        }
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
            ),
        )
        assertThat(pendingSyncDao.creates.value).isEmpty()
    }

    @Test
    fun `failed create keeps the user and queues a pending create`() = runTest {
        api.createResult = Result.Error(DataError.Network.NO_INTERNET)

        val result = repository.createUser("Grace", "grace@x.io")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(createdUserDao.rows.value.values.toList()).containsExactly(
            CreatedUserEntity(localId = 1, remoteId = null, name = "Grace", email = "grace@x.io", createdAt = 1_000),
        )
        assertThat(pendingSyncDao.creates.value.values.toList()).containsExactly(CreateUserPendingSyncEntity(1))
    }

    @Test
    fun `create rejected by GoRest removes the user and records the rejection`() = runTest {
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)

        val result = repository.createUser("Grace", "grace@x.io")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNPROCESSABLE_ENTITY))
        assertThat(createdUserDao.rows.value).isEmpty()
        assertThat(pendingSyncDao.creates.value).isEmpty()
        assertThat(repository.observeRejectedCreations().first())
            .containsExactly(RejectedCreation(id = 1, name = "Grace", email = "grace@x.io"))
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
            UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 1_000),
        )
    }

    @Test
    fun `updating an unsynced created user only edits the row`() = runTest {
        api.createResult = Result.Error(DataError.Network.NO_INTERNET)
        repository.createUser("Grace", "grace@x.io")
        api.calls.clear()

        val result = repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@h.io")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(createdUserDao.rows.value.getValue(1).name).isEqualTo("Grace H")
        assertThat(api.calls).isEmpty()
        assertThat(pendingSyncDao.updates.value).isEmpty()
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
    fun `deleting an unsynced created user drops it and its pending create without a request`() = runTest {
        api.createResult = Result.Error(DataError.Network.NO_INTERNET)
        repository.createUser("Grace", "grace@x.io")
        api.calls.clear()

        val result = repository.deleteUser(FeedUserId.Created(1))

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(createdUserDao.rows.value).isEmpty()
        assertThat(pendingSyncDao.creates.value).isEmpty()
        assertThat(api.calls).isEmpty()
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
        assertThat(pendingSyncDao.creates.value).isEmpty()
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
        assertThat(pendingSyncDao.creates.value.values.toList()).containsExactly(CreateUserPendingSyncEntity(1))
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
            UpdateUserPendingSyncEntity(1, "Ada M", "ada@m.io", updatedAt = 6),
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
        assertThat(pendingSyncDao.creates.value).isEmpty()
        assertThat(pendingSyncDao.updates.value).isEmpty()
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
        assertThat(pendingSyncDao.creates.value).isEmpty()
        assertThat(pendingSyncDao.deletes.value).isEmpty()
    }

    @Test
    fun `a pending create rejected by GoRest is removed with its user and recorded`() = runTest {
        queueOfflineCreate("Grace")
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)

        val result = repository.syncPendingChanges()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(createdUserDao.rows.value).isEmpty()
        assertThat(pendingSyncDao.creates.value).isEmpty()
        assertThat(repository.observeRejectedCreations().first())
            .containsExactly(RejectedCreation(id = 1, name = "Grace", email = "grace@x.io"))
    }

    // rejected creations

    @Test
    fun `dismissing a rejection removes only that one`() = runTest {
        api.createResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)
        repository.createUser("Grace", "grace@x.io")
        repository.createUser("Ada", "ada@x.io")

        repository.dismissRejectedCreation(1)

        assertThat(repository.observeRejectedCreations().first())
            .containsExactly(RejectedCreation(id = 2, name = "Ada", email = "ada@x.io"))
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
}
