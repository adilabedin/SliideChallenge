package com.androidlearning.sliide_challenge.userfeed.domain

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

/**
 * Every change is written to the database first, so [observeUsers] reflects it immediately, and then sent to GoRest.
 * If the request fails, the change is kept locally and queued for a later sync; the returned error only tells the
 * caller that it has not reached the server yet.
 */
interface UserFeedRepository {
    /** Users created on this device (newest first), then the cached page minus pending deletes, with pending edits applied. */
    fun observeUsers(): Flow<List<FeedUser>>

    suspend fun createUser(name: String, email: String): EmptyResult<DataError.Network>

    suspend fun updateUser(id: FeedUserId, name: String, email: String): EmptyResult<DataError.Network>

    suspend fun deleteUser(id: FeedUserId): EmptyResult<DataError.Network>
}
