package com.androidlearning.sliide_challenge.userfeed.domain

/**
 * Schedules the background sync of queued user changes: a periodic run of [UserChangesSync.sync] that sends whatever is
 * left, and per-change retries ([scheduleChangeSync]).
 */
interface UserSyncScheduler {
    /** Starts the periodic sync, which sends anything left pending, if it is not already scheduled. */
    fun scheduleSync()

    /**
     * Retries just [change] as soon as the network allows, backing off while it keeps failing; each run calls
     * [UserChangesSync.syncChange]. Platforms without per-change background work leave it to the periodic sync.
     */
    fun scheduleChangeSync(change: PendingUserChange)

    /** Cancels the periodic sync. Per-change retries finish on their own once their change is settled. */
    fun cancelSync()
}
