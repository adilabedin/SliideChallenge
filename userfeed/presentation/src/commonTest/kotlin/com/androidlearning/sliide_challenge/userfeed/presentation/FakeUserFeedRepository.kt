package com.androidlearning.sliide_challenge.userfeed.presentation

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.RejectedCreation
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Records every call. A create or update returns [nextResult], first waiting for [pendingSave] when one is set, so a
 * test can observe the state while a save is in flight. A 422 create is recorded as a rejection like the real one.
 */
class FakeUserFeedRepository : UserFeedRepository {
    val users = MutableStateFlow<List<FeedUser>>(emptyList())
    val rejectedCreations = MutableStateFlow<List<RejectedCreation>>(emptyList())

    var nextResult: EmptyResult<DataError.Network> = Result.Success(Unit)
    var pendingSave: CompletableDeferred<Unit>? = null

    val createCalls = mutableListOf<Pair<String, String>>()
    val updateCalls = mutableListOf<Triple<FeedUserId, String, String>>()

    /** Creates and updates that got past [pendingSave], i.e. were not cancelled while waiting. */
    var completedSaves = 0

    override fun observeUsers(): Flow<List<FeedUser>> = users

    override suspend fun createUser(name: String, email: String): EmptyResult<DataError.Network> {
        createCalls += name to email
        pendingSave?.await()
        completedSaves++
        val result = nextResult
        if (result is Result.Error && result.error == DataError.Network.UNPROCESSABLE_ENTITY) {
            rejectedCreations.update { it + RejectedCreation(id = it.size + 1L, name = name, email = email) }
        }
        return result
    }

    override suspend fun updateUser(id: FeedUserId, name: String, email: String): EmptyResult<DataError.Network> {
        updateCalls += Triple(id, name, email)
        pendingSave?.await()
        completedSaves++
        return nextResult
    }

    override suspend fun deleteUser(id: FeedUserId): EmptyResult<DataError.Network> = Result.Success(Unit)

    override fun observeHasPendingSyncs(): Flow<Boolean> = MutableStateFlow(false)

    override suspend fun syncPendingChanges(): EmptyResult<DataError.Network> = Result.Success(Unit)

    override fun observeRejectedCreations(): Flow<List<RejectedCreation>> = rejectedCreations

    override suspend fun dismissRejectedCreation(id: Long) {
        rejectedCreations.update { rejections -> rejections.filterNot { it.id == id } }
    }
}
