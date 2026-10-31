package com.androidlearning.sliide_challenge.userfeed.data.sync

import com.androidlearning.sliide_challenge.userfeed.domain.UserChangesSync
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * iOS counterpart of the Android `UserSyncWorker`: runs [UserChangesSync.sync] from a background app refresh and,
 * since iOS may run that rarely, every time the app becomes active (including launch).
 */
class IosUserSync(private val userChangesSync: UserChangesSync, private val userSyncScheduler: UserSyncScheduler) {
    /** iOS requires the task handler to be registered before the app finishes launching. */
    fun start(scope: CoroutineScope) {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = BackgroundTaskUserSyncScheduler.TASK_IDENTIFIER,
            usingQueue = null
        ) { task -> task?.let { runBackgroundTask(it, scope) } }
        // The repository schedules a sync for each change it saves; this catches changes left from an earlier run.
        scope.launch { userChangesSync.scheduleIfPending() }
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ -> scope.launch { userChangesSync.sync() } }
    }

    private fun runBackgroundTask(task: BGTask, scope: CoroutineScope) {
        // Book the next run first, so one is still booked if iOS expires this task mid-sync.
        userSyncScheduler.scheduleSync()
        val job = scope.launch { userChangesSync.sync() }
        task.expirationHandler = { job.cancel() }
        job.invokeOnCompletion { cause -> task.setTaskCompletedWithSuccess(cause == null) }
    }
}
