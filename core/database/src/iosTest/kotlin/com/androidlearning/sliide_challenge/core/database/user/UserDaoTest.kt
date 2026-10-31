package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.androidlearning.sliide_challenge.core.database.DatabaseFactory
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class UserDaoTest {

    private lateinit var database: SliideDatabase
    private lateinit var dao: UserDao

    @BeforeTest
    fun setUp() {
        database = DatabaseFactory.create(Room.inMemoryDatabaseBuilder<SliideDatabase>())
        dao = database.userDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun getAll_emptyTable_emitsEmptyList() = runTest {
        assertThat(dao.getAll().first()).isEmpty()
    }

    @Test
    fun insertAll_thenGetAll_returnsUsersNewestFirst() = runTest {
        val older = user(id = 1)
        val newer = user(id = 2)

        dao.insertAll(listOf(older, newer))

        assertThat(dao.getAll().first()).containsExactly(newer, older)
    }

    @Test
    fun insertAll_existingId_replacesRow() = runTest {
        dao.insertAll(listOf(user(id = 1, name = "Old")))

        dao.insertAll(listOf(user(id = 1, name = "New")))

        assertThat(dao.getAll().first()).containsExactly(user(id = 1, name = "New"))
    }

    @Test
    fun replaceAll_dropsRowsNotInNewList() = runTest {
        dao.insertAll(listOf(user(id = 1), user(id = 2)))

        dao.replaceAll(listOf(user(id = 3)))

        assertThat(dao.getAll().first()).containsExactly(user(id = 3))
    }

    @Test
    fun updateDetails_editsOnlyThatRowAndKeepsSyncTime() = runTest {
        dao.insertAll(listOf(user(id = 1), user(id = 2)))

        dao.updateDetails(id = 1, name = "Edited", email = "edited@example.com")

        assertThat(dao.getAll().first()).containsExactly(
            user(id = 2),
            user(id = 1, name = "Edited").copy(email = "edited@example.com")
        )
    }

    @Test
    fun updateDetails_missingId_doesNotInsert() = runTest {
        dao.updateDetails(id = 1, name = "Edited", email = "edited@example.com")

        assertThat(dao.getAll().first()).isEmpty()
    }

    @Test
    fun setLastSyncedAt_changesOnlyThatRow() = runTest {
        dao.insertAll(listOf(user(id = 1), user(id = 2)))

        dao.setLastSyncedAt(id = 1, syncedAt = 5_000)

        assertThat(dao.getAll().first()).containsExactly(user(id = 2), user(id = 1, lastSyncedAt = 5_000))
    }

    @Test
    fun deleteById_removesOnlyThatRow() = runTest {
        dao.insertAll(listOf(user(id = 1), user(id = 2)))

        dao.deleteById(1)

        assertThat(dao.getAll().first()).containsExactly(user(id = 2))
    }

    private fun user(id: Long, name: String = "User $id", lastSyncedAt: Long = 1_000) = UserEntity(
        id = id,
        name = name,
        email = "user$id@example.com",
        lastSyncedAt = lastSyncedAt
    )
}
