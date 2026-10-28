package com.androidlearning.sliide_challenge.userfeed.data

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.data.user.UserDto
import com.androidlearning.sliide_challenge.core.database.user.CreateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserDao
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserEntity
import com.androidlearning.sliide_challenge.core.database.user.DeleteUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UpdateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UserDao
import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.core.database.user.UserPendingSyncDao
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

        override suspend fun createUser(name: String, email: String) = createResult.also { calls += "POST" }

        override suspend fun updateUser(id: Long, name: String, email: String) =
            updateResult.also { calls += "PATCH $id" }

        override suspend fun deleteUser(id: Long) = deleteResult.also { calls += "DELETE $id" }
    }

    private class FakeUserDao : UserDao {
        val rows = MutableStateFlow<Map<Long, UserEntity>>(emptyMap())

        override suspend fun insertAll(users: List<UserEntity>) {
            rows.value += users.associateBy { it.id }
        }

        override suspend fun update(user: UserEntity) {
            if (user.id in rows.value) rows.value += user.id to user
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

        override suspend fun setRemoteId(localId: Long, remoteId: Long) {
            rows.value[localId]?.let { rows.value += localId to it.copy(remoteId = remoteId) }
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

        override suspend fun upsertDeletePendingSync(item: DeleteUserPendingSyncEntity) {
            deletes.value += item.userId to item
        }

        override suspend fun getDeletePendingSyncs() = deletes.value.values.toList()

        override fun observeDeletePendingSyncs(): Flow<List<DeleteUserPendingSyncEntity>> =
            deletes.map { it.values.toList() }

        override suspend fun deleteDeletePendingSync(userId: Long) {
            deletes.value -= userId
        }
    }

    private val now = Instant.fromEpochMilliseconds(1_000)
    private val api = FakeUserFeedApi()
    private val userDao = FakeUserDao()
    private val createdUserDao = FakeCreatedUserDao()
    private val pendingSyncDao = FakeUserPendingSyncDao()
    private val repository = OfflineFirstUserFeedRepository(
        userFeedApi = api,
        userDao = userDao,
        createdUserDao = createdUserDao,
        pendingSyncDao = pendingSyncDao,
        clock = object : Clock {
            override fun now() = this@OfflineFirstUserFeedRepositoryTest.now
        },
    )

    private val ada = UserEntity(1, "Ada", "ada@x.io")
    private val alan = UserEntity(2, "Alan", "alan@x.io")

    private suspend fun feed() = repository.observeUsers().first()

    // observeUsers

    @Test
    fun `created users come first and cached users follow`() = runTest {
        userDao.insertAll(listOf(ada, alan))
        createdUserDao.upsert(CreatedUserEntity(name = "Grace", email = "grace@x.io", createdAt = 1))

        assertThat(feed()).containsExactly(
            FeedUser(FeedUserId.Created(1), "Grace", "grace@x.io"),
            FeedUser(FeedUserId.Remote(2), "Alan", "alan@x.io"),
            FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io"),
        )
    }

    @Test
    fun `pending deletes are hidden and pending edits are overlaid on cached users`() = runTest {
        userDao.insertAll(listOf(ada, alan))
        pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(2))
        pendingSyncDao.upsertUpdatePendingSync(UpdateUserPendingSyncEntity(1, "Ada L", "ada@l.io", updatedAt = 0))

        assertThat(feed()).containsExactly(FeedUser(FeedUserId.Remote(1), "Ada L", "ada@l.io"))
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
            assertThat(awaitItem()).containsExactly(FeedUser(FeedUserId.Created(1), "Grace", "grace@x.io"))
            awaitItem() // remote id set after the 201

            repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@x.io")
            assertThat(awaitItem()).containsExactly(FeedUser(FeedUserId.Created(1), "Grace H", "grace@x.io"))

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
            CreatedUserEntity(localId = 1, remoteId = 100, name = "Grace", email = "grace@x.io", createdAt = 1_000),
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

    // updateUser

    @Test
    fun `updating a cached user writes it locally and patches it`() = runTest {
        userDao.insertAll(listOf(ada))

        val result = repository.updateUser(FeedUserId.Remote(1), "Ada L", "ada@l.io")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(userDao.rows.value.values.toList()).containsExactly(UserEntity(1, "Ada L", "ada@l.io"))
        assertThat(api.calls).containsExactly("PATCH 1")
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
        assertThat(userDao.rows.value.values.toList()).containsExactly(UserEntity(1, "Ada L", "ada@l.io"))
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
    fun `updating a synced created user edits the row and patches its remote id`() = runTest {
        repository.createUser("Grace", "grace@x.io")
        api.updateResult = Result.Error(DataError.Network.NO_INTERNET)

        repository.updateUser(FeedUserId.Created(1), "Grace H", "grace@h.io")

        assertThat(createdUserDao.rows.value.getValue(1).name).isEqualTo("Grace H")
        assertThat(api.calls).containsExactly("POST", "PATCH 100")
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
        assertThat(api.calls).containsExactly("POST", "DELETE 100")
        assertThat(pendingSyncDao.deletes.value.values.toList()).containsExactly(DeleteUserPendingSyncEntity(100))
    }
}
