package com.androidlearning.sliide_challenge.userfeed.domain

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

/**
 * Every change is written to the database and queued for sync first, so [observeUsers] reflects it immediately and it
 * survives the app dying, and then sent to GoRest. If the request fails, the change stays queued for a later sync; the
 * returned error only tells the caller that it has not reached the server yet. A change finishes even if the caller is
 * cancelled meanwhile.
 */
interface UserFeedRepository {
    /** Users created on this device (newest first), then the cached page minus pending deletes, with pending edits applied. */
    fun observeUsers(): Flow<List<FeedUser>>

    /**
     * If GoRest rejects the user ([DataError.Network.UNPROCESSABLE_ENTITY], e.g. email already taken), it is removed
     * again and reported through [observeRejectedCreations]; after any other failure it stays queued for a later sync.
     */
    suspend fun createUser(name: String, email: String): EmptyResult<DataError.Network>

    /**
     * Pull-to-refresh: sends queued changes, then replaces the cached page with the last GoRest page. Users created
     * on this device live in their own table, so they stay at the top whatever comes back. On failure the cache is
     * kept and queued changes stay queued.
     */
    suspend fun refreshUsers(): EmptyResult<DataError.Network>

    suspend fun updateUser(id: FeedUserId, name: String, email: String): EmptyResult<DataError.Network>

    suspend fun deleteUser(id: FeedUserId): EmptyResult<DataError.Network>

    /** True while any local change is still waiting to reach GoRest. */
    fun observeHasPendingSyncs(): Flow<Boolean>

    /**
     * Sends every queued change. Each one is removed from the queue only once GoRest accepts it (or reports the user
     * as gone); anything else stays queued for the next sync. Returns the first error if any change failed.
     */
    suspend fun syncPendingChanges(): EmptyResult<DataError.Network>

    /** Created users GoRest refused, whether on create or in a later sync, oldest first, until dismissed. */
    fun observeRejectedCreations(): Flow<List<RejectedCreation>>

    suspend fun dismissRejectedCreation(id: Long)
}
