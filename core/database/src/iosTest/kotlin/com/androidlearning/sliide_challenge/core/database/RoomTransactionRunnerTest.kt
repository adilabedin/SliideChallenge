package com.androidlearning.sliide_challenge.core.database

import androidx.room.Room
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserEntity
import com.androidlearning.sliide_challenge.core.database.user.DeleteUserPendingSyncEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class RoomTransactionRunnerTest {

    private lateinit var database: SliideDatabase
    private lateinit var runner: RoomTransactionRunner

    @BeforeTest
    fun setUp() {
        database = DatabaseFactory.create(Room.inMemoryDatabaseBuilder<SliideDatabase>())
        runner = RoomTransactionRunner(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun runInTransaction_success_keepsEveryWriteAndReturnsTheResult() = runTest {
        val localId = runner.runInTransaction { writeUserAndQueueEntry() }

        assertThat(database.createdUserDao().getAll().first().map { it.localId }).containsExactly(localId)
        assertThat(database.userPendingSyncDao().getDeletePendingSyncs())
            .containsExactly(DeleteUserPendingSyncEntity(QUEUED_DELETE_ID))
    }

    @Test
    fun runInTransaction_failureAfterWrites_rollsBackEveryWrite() = runTest {
        assertFailure {
            runner.runInTransaction {
                writeUserAndQueueEntry()
                error("Failed before the transaction finished")
            }
        }.transform { it.message }.isEqualTo("Failed before the transaction finished")

        assertThat(database.createdUserDao().getAll().first()).isEmpty()
        assertThat(database.userPendingSyncDao().getDeletePendingSyncs()).isEmpty()
    }

    /** Writes to two tables through two DAOs, as the repository does. */
    private suspend fun writeUserAndQueueEntry(): Long {
        val localId = database.createdUserDao().upsert(
            CreatedUserEntity(name = "Ada", email = "ada@example.com", createdAt = 1_000)
        )
        database.userPendingSyncDao().upsertDeletePendingSync(DeleteUserPendingSyncEntity(QUEUED_DELETE_ID))
        return localId
    }

    private companion object {
        const val QUEUED_DELETE_ID = 7L
    }
}
