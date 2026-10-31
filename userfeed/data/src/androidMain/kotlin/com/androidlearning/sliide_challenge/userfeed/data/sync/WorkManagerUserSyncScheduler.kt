package com.androidlearning.sliide_challenge.userfeed.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.androidlearning.sliide_challenge.userfeed.domain.PendingUserChange
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import java.util.concurrent.TimeUnit

/**
 * Runs [UserSyncWorker] every 30 minutes while there is a network connection (the first run as soon as one is
 * available), plus a [UserChangeSyncWorker] per failed change, which retries with exponential backoff.
 */
class WorkManagerUserSyncScheduler(private val context: Context) : UserSyncScheduler {

    override fun scheduleSync() {
        val request = PeriodicWorkRequestBuilder<UserSyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        // KEEP so scheduling again (e.g. on every app start) doesn't reset the 30-minute period.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun scheduleChangeSync(change: PendingUserChange) {
        val request = OneTimeWorkRequestBuilder<UserChangeSyncWorker>()
            .setInputData(UserChangeSyncWorker.inputDataOf(change))
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CHANGE_RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        // KEEP, so a change already waiting to retry keeps its place in the backoff instead of starting over. The
        // worker re-reads the change, so it always sends the latest edit.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UserChangeSyncWorker.uniqueWorkName(change), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelSync() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val UNIQUE_WORK_NAME = "sync_pending_users"
        const val SYNC_INTERVAL_MINUTES = 30L
        const val CHANGE_RETRY_BACKOFF_SECONDS = 30L
    }
}
