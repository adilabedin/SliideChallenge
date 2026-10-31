package com.androidlearning.sliide_challenge.userfeed.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.androidlearning.sliide_challenge.core.data.work.WorkerResult
import com.androidlearning.sliide_challenge.core.domain.util.Result as DomainResult
import com.androidlearning.sliide_challenge.userfeed.domain.PendingUserChange
import com.androidlearning.sliide_challenge.userfeed.domain.UserChangesSync

/**
 * Retries one failed change ([UserChangesSync.syncChange]). It retries with backoff while the change is still pending,
 * and succeeds once GoRest has settled it, whichever send got there. Each change is its own unique work (see
 * [WorkManagerUserSyncScheduler]), so the WorkManager inspector shows which change is failing.
 */
class UserChangeSyncWorker(context: Context, params: WorkerParameters, private val userChangesSync: UserChangesSync) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): WorkerResult {
        // Unreadable input can't be retried into something valid; the periodic sync still covers the change.
        val change = inputData.toPendingUserChange() ?: return WorkerResult.failure()
        return when (userChangesSync.syncChange(change)) {
            is DomainResult.Success -> WorkerResult.success()
            is DomainResult.Error -> WorkerResult.retry()
        }
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_ID = "id"
        private const val TYPE_CREATE = "create"
        private const val TYPE_UPDATE = "update"
        private const val TYPE_DELETE = "delete"

        fun inputDataOf(change: PendingUserChange): Data = when (change) {
            is PendingUserChange.Create -> workDataOf(KEY_TYPE to TYPE_CREATE, KEY_ID to change.localId)
            is PendingUserChange.Update -> workDataOf(KEY_TYPE to TYPE_UPDATE, KEY_ID to change.userId)
            is PendingUserChange.Delete -> workDataOf(KEY_TYPE to TYPE_DELETE, KEY_ID to change.userId)
        }

        /** Unique per change, e.g. `sync_user_change_update_42`, so a change never has two retries queued. */
        fun uniqueWorkName(change: PendingUserChange): String = when (change) {
            is PendingUserChange.Create -> "sync_user_change_${TYPE_CREATE}_${change.localId}"
            is PendingUserChange.Update -> "sync_user_change_${TYPE_UPDATE}_${change.userId}"
            is PendingUserChange.Delete -> "sync_user_change_${TYPE_DELETE}_${change.userId}"
        }

        private fun Data.toPendingUserChange(): PendingUserChange? {
            val id = getLong(KEY_ID, -1).takeIf { it >= 0 } ?: return null
            return when (getString(KEY_TYPE)) {
                TYPE_CREATE -> PendingUserChange.Create(id)
                TYPE_UPDATE -> PendingUserChange.Update(id)
                TYPE_DELETE -> PendingUserChange.Delete(id)
                else -> null
            }
        }
    }
}
