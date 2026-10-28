package com.androidlearning.sliide_challenge.userfeed.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.androidlearning.sliide_challenge.core.data.work.WorkerResult
import com.androidlearning.sliide_challenge.core.domain.util.Result as DomainResult
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository

/**
 * Sends the queued user changes, then lets [PendingSyncScheduling] cancel the schedule if nothing is left.
 * Changes that fail stay queued, and periodic work runs again next period even after `failure()`, so a failure needs
 * no retry of its own.
 */
class UserSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val userFeedRepository: UserFeedRepository,
    private val pendingSyncScheduling: PendingSyncScheduling,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): WorkerResult {
        val result = userFeedRepository.syncPendingChanges()
        pendingSyncScheduling.onSyncFinished()
        return when (result) {
            is DomainResult.Success -> WorkerResult.success()
            is DomainResult.Error -> WorkerResult.failure()
        }
    }
}
