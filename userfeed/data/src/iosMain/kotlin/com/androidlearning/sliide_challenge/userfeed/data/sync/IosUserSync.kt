package com.androidlearning.sliide_challenge.userfeed.data.sync

import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * iOS counterpart of the Android `UserSyncWorker`: sends queued changes from a background app refresh and, since iOS
 * may run that rarely, every time the app becomes active (including launch).
 */
class IosUserSync(
    private val userFeedRepository: UserFeedRepository,
    private val userSyncScheduler: UserSyncScheduler,
    private val pendingSyncScheduling: PendingSyncScheduling,
) {
    /** iOS requires the task handler to be registered before the app finishes launching. */
    fun start(scope: CoroutineScope) {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = BackgroundTaskUserSyncScheduler.TASK_IDENTIFIER,
            usingQueue = null,
        ) { task -> task?.let { runBackgroundTask(it, scope) } }
        pendingSyncScheduling.start(scope)
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> scope.launch { sync() } }
    }

    private fun runBackgroundTask(task: BGTask, scope: CoroutineScope) {
        // Book the next run first, so one is still booked if iOS expires this task mid-sync.
        userSyncScheduler.scheduleSync()
        val job = scope.launch { sync() }
        task.expirationHandler = { job.cancel() }
        job.invokeOnCompletion { cause -> task.setTaskCompletedWithSuccess(cause == null) }
    }

    /** Failed changes stay queued; [PendingSyncScheduling.onSyncFinished] cancels the booked run once none are left. */
    private suspend fun sync() {
        userFeedRepository.syncPendingChanges()
        pendingSyncScheduling.onSyncFinished()
    }
}
