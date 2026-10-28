package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import com.androidlearning.sliide_challenge.core.database.DatabaseFactory
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RejectedCreationDaoTest {

    private lateinit var database: SliideDatabase
    private lateinit var dao: RejectedCreationDao

    @BeforeTest
    fun setUp() {
        database = DatabaseFactory.create(Room.inMemoryDatabaseBuilder<SliideDatabase>())
        dao = database.rejectedCreationDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_observe_emitsOldestFirst() = runTest {
        dao.insert(RejectedCreationEntity(name = "Grace", email = "grace@x.io"))
        dao.insert(RejectedCreationEntity(name = "Ada", email = "ada@x.io"))

        assertThat(dao.observeAll().first()).containsExactly(
            RejectedCreationEntity(id = 1, name = "Grace", email = "grace@x.io"),
            RejectedCreationEntity(id = 2, name = "Ada", email = "ada@x.io"),
        )
    }

    @Test
    fun delete_removesOnlyThatEntry() = runTest {
        dao.insert(RejectedCreationEntity(name = "Grace", email = "grace@x.io"))
        dao.insert(RejectedCreationEntity(name = "Ada", email = "ada@x.io"))

        dao.deleteById(1)

        assertThat(dao.observeAll().first())
            .containsExactly(RejectedCreationEntity(id = 2, name = "Ada", email = "ada@x.io"))
    }
}
