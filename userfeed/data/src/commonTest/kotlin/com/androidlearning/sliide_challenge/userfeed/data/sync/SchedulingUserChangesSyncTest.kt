package com.androidlearning.sliide_challenge.userfeed.data.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.PendingUserChange
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class SchedulingUserChangesSyncTest {

    private class FakeUserFeedRepository : UserFeedRepository {
        val hasPendingSyncs = MutableStateFlow(false)

        override fun observeHasPendingSyncs(): Flow<Boolean> = hasPendingSyncs

        override fun observeUsers(): Flow<List<FeedUser>> = emptyFlow()

        override suspend fun createUser(name: String, email: String): EmptyResult<DataError.Network> =
            Result.Success(Unit)

        override suspend fun loadUsers(): EmptyResult<DataError.Network> = Result.Success(Unit)

        override suspend fun loadUsersIfNotCached(): EmptyResult<DataError.Network> = Result.Success(Unit)

        override suspend fun updateUser(id: FeedUserId, name: String, email: String): EmptyResult<DataError.Network> =
            Result.Success(Unit)

        override suspend fun deleteUser(id: FeedUserId): EmptyResult<DataError.Network> = Result.Success(Unit)

        var syncResult: EmptyResult<DataError.Network> = Result.Success(Unit)
        var syncCount = 0

        /** Runs while the changes are being sent, to simulate the queue changing meanwhile. */
        var duringSync: () -> Unit = {}

        override suspend fun syncPendingChanges(): EmptyResult<DataError.Network> {
            syncCount++
            duringSync()
            return syncResult
        }

        val syncedChanges = mutableListOf<PendingUserChange>()

        override suspend fun syncChange(change: PendingUserChange): EmptyResult<DataError.Network> {
            syncedChanges += change
            return syncResult
        }
    }

    private class FakeUserSyncScheduler : UserSyncScheduler {
        val calls = mutableListOf<String>()

        /** Runs as the cancel is issued, to simulate a change being queued at that moment. */
        var duringCancel: () -> Unit = {}

        override fun scheduleSync() {
            calls += "schedule"
        }

        override fun scheduleChangeSync(change: PendingUserChange) {
            calls += "schedule $change"
        }

        override fun cancelSync() {
            calls += "cancel"
            duringCancel()
        }
    }

    private val repository = FakeUserFeedRepository()
    private val scheduler = FakeUserSyncScheduler()
    private val sync = SchedulingUserChangesSync(repository, scheduler)

    @Test
    fun `nothing pending at launch schedules nothing`() = runTest {
        sync.scheduleIfPending()

        assertThat(scheduler.calls).isEmpty()
    }

    @Test
    fun `changes left pending at launch schedule a sync`() = runTest {
        repository.hasPendingSyncs.value = true

        sync.scheduleIfPending()

        assertThat(scheduler.calls).containsExactly("schedule")
    }

    @Test
    fun `a sync sends the queued changes and returns their outcome`() = runTest {
        repository.syncResult = Result.Error(DataError.Network.NO_INTERNET)

        val result = sync.sync()

        assertThat(repository.syncCount).isEqualTo(1)
        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }

    @Test
    fun `a finished sync cancels the schedule once nothing is pending`() = runTest {
        repository.hasPendingSyncs.value = true
        repository.duringSync = { repository.hasPendingSyncs.value = false }

        sync.sync()

        assertThat(scheduler.calls).containsExactly("cancel")
    }

    @Test
    fun `a finished sync keeps the schedule while changes are still pending`() = runTest {
        repository.hasPendingSyncs.value = true

        sync.sync()

        assertThat(scheduler.calls).isEmpty()
    }

    @Test
    fun `a change queued while the schedule is cancelled schedules it again`() = runTest {
        scheduler.duringCancel = { repository.hasPendingSyncs.value = true }

        sync.sync()

        assertThat(scheduler.calls).containsExactly("cancel", "schedule")
    }

    @Test
    fun `a change sync sends just that change and returns its outcome without touching the schedule`() = runTest {
        repository.syncResult = Result.Error(DataError.Network.SERVER_ERROR)

        val result = sync.syncChange(PendingUserChange.Update(7))

        assertThat(repository.syncedChanges).containsExactly(PendingUserChange.Update(7))
        assertThat(repository.syncCount).isEqualTo(0)
        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
        assertThat(scheduler.calls).isEmpty()
    }
}
