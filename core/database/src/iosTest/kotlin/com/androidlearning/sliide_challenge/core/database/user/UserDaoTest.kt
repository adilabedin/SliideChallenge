package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.androidlearning.sliide_challenge.core.database.DatabaseFactory
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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

    private fun user(id: Long, name: String = "User $id") = UserEntity(
        id = id,
        name = name,
        email = "user$id@example.com",
    )
}
