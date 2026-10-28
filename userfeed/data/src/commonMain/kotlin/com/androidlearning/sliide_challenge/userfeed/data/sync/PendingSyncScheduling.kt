package com.androidlearning.sliide_challenge.userfeed.data.sync

import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * Keeps the background sync scheduled only while there are queued changes, so nothing wakes up for an empty queue.
 * Changes starting to pend schedule it; only a finished sync cancels it, because the queue can empty mid-sync (e.g. a
 * created user deleted while its POST is in flight) and cancelling then would stop the running sync.
 */
class PendingSyncScheduling(
    private val userFeedRepository: UserFeedRepository,
    private val userSyncScheduler: UserSyncScheduler,
) {
    fun start(scope: CoroutineScope) {
        userFeedRepository.observeHasPendingSyncs()
            .distinctUntilChanged()
            .onEach { hasPending -> if (hasPending) userSyncScheduler.scheduleSync() }
            .launchIn(scope)
    }

    /** Called as the last step of a sync. Cancelling stops the calling sync, which has nothing left to do. */
    suspend fun onSyncFinished() {
        if (hasPendingSyncs()) return
        userSyncScheduler.cancelSync()
        // A change queued just before the cancel took effect would otherwise wait for the next app start.
        withContext(NonCancellable) {
            if (hasPendingSyncs()) userSyncScheduler.scheduleSync()
        }
    }

    private suspend fun hasPendingSyncs() = userFeedRepository.observeHasPendingSyncs().first()
}
