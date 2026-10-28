package com.androidlearning.sliide_challenge.userfeed.data.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.RejectedCreation
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingSyncSchedulingTest {

    private class FakeUserFeedRepository : UserFeedRepository {
        val hasPendingSyncs = MutableStateFlow(false)

        override fun observeHasPendingSyncs(): Flow<Boolean> = hasPendingSyncs

        override fun observeUsers(): Flow<List<FeedUser>> = emptyFlow()

        override suspend fun createUser(name: String, email: String): EmptyResult<DataError.Network> =
            Result.Success(Unit)

        override suspend fun updateUser(id: FeedUserId, name: String, email: String): EmptyResult<DataError.Network> =
            Result.Success(Unit)

        override suspend fun deleteUser(id: FeedUserId): EmptyResult<DataError.Network> = Result.Success(Unit)

        override suspend fun syncPendingChanges(): EmptyResult<DataError.Network> = Result.Success(Unit)

        override fun observeRejectedCreations(): Flow<List<RejectedCreation>> = MutableStateFlow(emptyList())

        override suspend fun dismissRejectedCreation(id: Long) = Unit
    }

    private class FakeUserSyncScheduler : UserSyncScheduler {
        val calls = mutableListOf<String>()

        /** Runs as the cancel is issued, to simulate a change being queued at that moment. */
        var duringCancel: () -> Unit = {}

        override fun scheduleSync() {
            calls += "schedule"
        }

        override fun cancelSync() {
            calls += "cancel"
            duringCancel()
        }
    }

    private val repository = FakeUserFeedRepository()
    private val scheduler = FakeUserSyncScheduler()
    private val scheduling = PendingSyncScheduling(repository, scheduler)

    @Test
    fun `nothing pending at start schedules nothing`() = runTest {
        scheduling.start(backgroundScope + UnconfinedTestDispatcher(testScheduler))

        assertThat(scheduler.calls).isEmpty()
    }

    @Test
    fun `sync is scheduled each time changes start pending and never cancelled by the queue emptying`() = runTest {
        scheduling.start(backgroundScope + UnconfinedTestDispatcher(testScheduler))

        repository.hasPendingSyncs.value = true
        // Emptying can happen mid-sync, e.g. deleting a created user whose POST is in flight; cancelling then would
        // stop the running sync.
        repository.hasPendingSyncs.value = false
        repository.hasPendingSyncs.value = true

        assertThat(scheduler.calls).containsExactly("schedule", "schedule")
    }

    @Test
    fun `sync is scheduled once however many changes queue up`() = runTest {
        repository.hasPendingSyncs.value = true
        scheduling.start(backgroundScope + UnconfinedTestDispatcher(testScheduler))

        repository.hasPendingSyncs.emit(true)

        assertThat(scheduler.calls).containsExactly("schedule")
    }

    @Test
    fun `a finished sync cancels the schedule once nothing is pending`() = runTest {
        scheduling.onSyncFinished()

        assertThat(scheduler.calls).containsExactly("cancel")
    }

    @Test
    fun `a finished sync keeps the schedule while changes are still pending`() = runTest {
        repository.hasPendingSyncs.value = true

        scheduling.onSyncFinished()

        assertThat(scheduler.calls).isEmpty()
    }

    @Test
    fun `a change queued while the schedule is cancelled schedules it again`() = runTest {
        scheduler.duringCancel = { repository.hasPendingSyncs.value = true }

        scheduling.onSyncFinished()

        assertThat(scheduler.calls).containsExactly("cancel", "schedule")
    }
}
