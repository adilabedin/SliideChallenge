package com.androidlearning.sliide_challenge.userfeed.domain

/** Schedules the background sync of queued user changes; the sync itself is [UserFeedRepository.syncPendingChanges]. */
interface UserSyncScheduler {
    /** Starts the periodic sync if it is not already scheduled. */
    fun scheduleSync()

    fun cancelSync()
}
