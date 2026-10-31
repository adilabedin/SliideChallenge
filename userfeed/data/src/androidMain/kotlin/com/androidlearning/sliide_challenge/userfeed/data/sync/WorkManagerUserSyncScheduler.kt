package com.androidlearning.sliide_challenge.userfeed.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import java.util.concurrent.TimeUnit

/**
 * Runs [UserSyncWorker] every 30 minutes while there is a network connection. The first run happens as soon as one
 * is available.
 */
class WorkManagerUserSyncScheduler(private val context: Context) : UserSyncScheduler {

    override fun scheduleSync() {
        val request = PeriodicWorkRequestBuilder<UserSyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        // KEEP so scheduling again (e.g. on every app start) doesn't reset the 30-minute period.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelSync() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "sync_pending_users"
        const val SYNC_INTERVAL_MINUTES = 30L
    }
}
