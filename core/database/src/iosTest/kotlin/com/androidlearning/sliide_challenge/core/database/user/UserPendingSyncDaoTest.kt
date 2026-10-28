package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.androidlearning.sliide_challenge.core.database.DatabaseFactory
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UserPendingSyncDaoTest {

    private lateinit var database: SliideDatabase
    private lateinit var dao: UserPendingSyncDao

    @BeforeTest
    fun setUp() {
        database = DatabaseFactory.create(Room.inMemoryDatabaseBuilder<SliideDatabase>())
        dao = database.userPendingSyncDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun createPendingSync_upsertTwice_keepsOneEntry() = runTest {
        dao.upsertCreatePendingSync(CreateUserPendingSyncEntity(localId = 1))
        dao.upsertCreatePendingSync(CreateUserPendingSyncEntity(localId = 1))

        assertThat(dao.getCreatePendingSyncs()).containsExactly(CreateUserPendingSyncEntity(localId = 1))
    }

    @Test
    fun createPendingSync_delete_removesOnlyThatEntry() = runTest {
        dao.upsertCreatePendingSync(CreateUserPendingSyncEntity(localId = 1))
        dao.upsertCreatePendingSync(CreateUserPendingSyncEntity(localId = 2))

        dao.deleteCreatePendingSync(localId = 1)

        assertThat(dao.getCreatePendingSyncs()).containsExactly(CreateUserPendingSyncEntity(localId = 2))
    }

    @Test
    fun updatePendingSync_sameUserTwice_keepsLatestEdit() = runTest {
        dao.upsertUpdatePendingSync(update(userId = 7, name = "First", updatedAt = 1_000))
        dao.upsertUpdatePendingSync(update(userId = 7, name = "Second", updatedAt = 2_000))

        assertThat(dao.getUpdatePendingSyncs())
            .containsExactly(update(userId = 7, name = "Second", updatedAt = 2_000))
    }

    @Test
    fun updatePendingSync_observe_emitsQueuedEdits() = runTest {
        dao.upsertUpdatePendingSync(update(userId = 7))
        dao.upsertUpdatePendingSync(update(userId = 8))

        assertThat(dao.observeUpdatePendingSyncs().first())
            .containsExactlyInAnyOrder(update(userId = 7), update(userId = 8))
    }

    @Test
    fun updatePendingSync_delete_removesEntry() = runTest {
        dao.upsertUpdatePendingSync(update(userId = 7))

        dao.deleteUpdatePendingSync(userId = 7)

        assertThat(dao.getUpdatePendingSyncs()).isEmpty()
    }

    @Test
    fun deletePendingSync_upsertTwice_keepsOneEntry() = runTest {
        dao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(userId = 7))
        dao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(userId = 7))

        assertThat(dao.observeDeletePendingSyncs().first())
            .containsExactly(DeleteUserPendingSyncEntity(userId = 7))
    }

    @Test
    fun deletePendingSync_delete_removesOnlyThatEntry() = runTest {
        dao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(userId = 7))
        dao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(userId = 8))

        dao.deleteDeletePendingSync(userId = 7)

        assertThat(dao.getDeletePendingSyncs()).containsExactly(DeleteUserPendingSyncEntity(userId = 8))
    }

    private fun update(userId: Long, name: String = "User $userId", updatedAt: Long = 1_000) =
        UpdateUserPendingSyncEntity(
            userId = userId,
            name = name,
            email = "user$userId@example.com",
            updatedAt = updatedAt,
        )
}
