package com.androidlearning.sliide_challenge.userfeed.domain

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult

/**
 * Owns the background sync of queued user changes: a run sends them and then keeps the [UserSyncScheduler] schedule in
 * step with what is left. The platform hosts (the Android worker, the iOS background task) only decide when to call
 * it. The repository still schedules a sync each time it saves a pending change; this covers what it can't.
 */
interface UserChangesSync {
    /**
     * Called once at launch. Schedules a sync if changes are pending: one saved just before the app died, too early
     * for the repository to have asked for a sync, or a schedule cancelled while changes were still pending (e.g. iOS
     * dropping a booked run).
     */
    suspend fun scheduleIfPending()

    /**
     * One sync run: sends every queued change, then cancels the schedule if nothing is left. Failed changes stay
     * queued, and the first error is returned.
     */
    suspend fun sync(): EmptyResult<DataError.Network>

    /**
     * One retry of a single change ([UserFeedRepository.syncChange]). Returns an error only while the change is still
     * pending, so the caller should retry it later. It never cancels the periodic schedule; only a full [sync] does.
     */
    suspend fun syncChange(change: PendingUserChange): EmptyResult<DataError.Network>
}
