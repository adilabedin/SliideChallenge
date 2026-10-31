package com.androidlearning.sliide_challenge.userfeed.data.sync

import com.androidlearning.sliide_challenge.userfeed.domain.PendingUserChange
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * Books a background app refresh no earlier than 30 minutes from now. iOS decides when (or whether) it actually runs,
 * and each request is used up once it launches, so [IosUserSync] books the next one at the start of every run.
 */
class BackgroundTaskUserSyncScheduler : UserSyncScheduler {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun scheduleSync() {
        val request = BGAppRefreshTaskRequest(identifier = TASK_IDENTIFIER).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(SYNC_INTERVAL_SECONDS)
        }
        // Submitting again replaces the earlier request. Fails on the simulator, which has no background tasks.
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
                println("Background sync not scheduled: ${error.value?.localizedDescription}")
            }
        }
    }

    /**
     * iOS has no per-change background work (no network constraint or backoff for one task per change), so a failed
     * change waits for the refresh booked when it was saved, or for the sync that runs whenever the app becomes active.
     * Rebooking here would only push that refresh further out.
     */
    override fun scheduleChangeSync(change: PendingUserChange) = Unit

    override fun cancelSync() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)
    }

    companion object {
        /** Must match `BGTaskSchedulerPermittedIdentifiers` in the iOS app's Info.plist. */
        const val TASK_IDENTIFIER = "com.androidlearning.sliide_challenge.sync-pending-users"
        private const val SYNC_INTERVAL_SECONDS = 30 * 60.0
    }
}
