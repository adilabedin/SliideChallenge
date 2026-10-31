package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import com.androidlearning.sliide_challenge.core.database.DatabaseFactory
import com.androidlearning.sliide_challenge.core.database.SliideDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class CreatedUserDaoTest {

    private lateinit var database: SliideDatabase
    private lateinit var dao: CreatedUserDao

    @BeforeTest
    fun setUp() {
        database = DatabaseFactory.create(Room.inMemoryDatabaseBuilder<SliideDatabase>())
        dao = database.createdUserDao()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsert_newUser_generatesLocalIdWithoutRemoteId() = runTest {
        val localId = dao.upsert(createdUser(name = "Ada"))

        val stored = dao.getByLocalId(localId)
        assertThat(localId).isNotEqualTo(0L)
        assertThat(stored?.name).isEqualTo("Ada")
        assertThat(stored?.remoteId).isNull()
    }

    @Test
    fun getAll_returnsNewestCreationFirst() = runTest {
        val olderId = dao.upsert(createdUser(name = "Older", createdAt = 1_000))
        val newerId = dao.upsert(createdUser(name = "Newer", createdAt = 2_000))

        assertThat(dao.getAll().first().map { it.localId }).containsExactly(newerId, olderId)
    }

    @Test
    fun upsert_existingLocalId_updatesInPlace() = runTest {
        val localId = dao.upsert(createdUser(name = "Old"))

        dao.upsert(dao.getByLocalId(localId)!!.copy(name = "New"))

        assertThat(dao.getAll().first().map { it.name }).containsExactly("New")
    }

    @Test
    fun setRemoteId_keepsRecordAndStoresServerIdAndSyncTime() = runTest {
        val localId = dao.upsert(createdUser())

        dao.setRemoteId(localId, remoteId = 42, syncedAt = 5_000)

        val user = dao.getByLocalId(localId)!!
        assertThat(user.remoteId).isEqualTo(42L)
        assertThat(user.lastSyncedAt).isEqualTo(5_000L)
    }

    @Test
    fun setLastSyncedAt_matchesByRemoteId() = runTest {
        val syncedId = dao.upsert(createdUser(name = "Synced"))
        val unsyncedId = dao.upsert(createdUser(name = "Unsynced"))
        dao.setRemoteId(syncedId, remoteId = 42, syncedAt = 1_000)

        dao.setLastSyncedAt(remoteId = 42, syncedAt = 5_000)

        assertThat(dao.getByLocalId(syncedId)?.lastSyncedAt).isEqualTo(5_000L)
        assertThat(dao.getByLocalId(unsyncedId)?.lastSyncedAt).isNull()
    }

    @Test
    fun updateDetails_changesNameAndEmailButKeepsRemoteId() = runTest {
        val localId = dao.upsert(createdUser())
        dao.setRemoteId(localId, remoteId = 42, syncedAt = 1_000)

        dao.updateDetails(localId, name = "New", email = "new@example.com")

        val user = dao.getByLocalId(localId)!!
        assertThat(user.name).isEqualTo("New")
        assertThat(user.email).isEqualTo("new@example.com")
        assertThat(user.remoteId).isEqualTo(42L)
    }

    @Test
    fun deleteByLocalId_removesOnlyThatUser() = runTest {
        val keptId = dao.upsert(createdUser(name = "Kept", createdAt = 1_000))
        val removedId = dao.upsert(createdUser(name = "Removed", createdAt = 2_000))

        dao.deleteByLocalId(removedId)

        assertThat(dao.getAll().first().map { it.localId }).containsExactly(keptId)
    }

    private fun createdUser(name: String = "User", createdAt: Long = 1_000) = CreatedUserEntity(
        name = name,
        email = "${name.lowercase()}@example.com",
        createdAt = createdAt
    )
}
