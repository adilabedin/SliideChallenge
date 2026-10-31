package com.androidlearning.sliide_challenge.userfeed.data.sync

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.userfeed.domain.PendingUserChange
import com.androidlearning.sliide_challenge.userfeed.domain.UserChangesSync
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The repository sends the changes ([UserFeedRepository.syncPendingChanges]); this decides what the schedule does
 * around a run, so both platforms share it.
 */
class SchedulingUserChangesSync(
    private val userFeedRepository: UserFeedRepository,
    private val userSyncScheduler: UserSyncScheduler
) : UserChangesSync {

    override suspend fun scheduleIfPending() {
        if (hasPendingSyncs()) userSyncScheduler.scheduleSync()
    }

    override suspend fun sync(): EmptyResult<DataError.Network> {
        val result = userFeedRepository.syncPendingChanges()
        cancelScheduleIfDone()
        return result
    }

    override suspend fun syncChange(change: PendingUserChange): EmptyResult<DataError.Network> =
        userFeedRepository.syncChange(change)

    /**
     * Only a finished run cancels the schedule, because the queue can empty mid-run (e.g. a created user deleted while
     * its POST is in flight) and cancelling then would stop the running sync. Cancelling stops the calling run, which
     * has nothing left to do.
     */
    private suspend fun cancelScheduleIfDone() {
        if (hasPendingSyncs()) return
        userSyncScheduler.cancelSync()
        // A change saved just before the cancel took effect asked for a sync the cancel then removed.
        withContext(NonCancellable) {
            if (hasPendingSyncs()) userSyncScheduler.scheduleSync()
        }
    }

    private suspend fun hasPendingSyncs() = userFeedRepository.observeHasPendingSyncs().first()
}
