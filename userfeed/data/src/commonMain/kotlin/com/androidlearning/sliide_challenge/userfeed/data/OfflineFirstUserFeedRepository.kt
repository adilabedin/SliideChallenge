package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.database.user.CreateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserDao
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserEntity
import com.androidlearning.sliide_challenge.core.database.user.DeleteUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UpdateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UserDao
import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.core.database.user.UserPendingSyncDao
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.core.domain.util.asEmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.onFailure
import com.androidlearning.sliide_challenge.core.domain.util.onSuccess
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock

/**
 * Room is the single source of truth. Each change is written locally first, then sent to GoRest; a failed request is
 * queued in the pending-sync tables (nothing drains them yet). Changes to a created user that has not been POSTed
 * stay local, since its pending create will carry them.
 */
class OfflineFirstUserFeedRepository(
    private val userFeedApi: UserFeedApi,
    private val userDao: UserDao,
    private val createdUserDao: CreatedUserDao,
    private val pendingSyncDao: UserPendingSyncDao,
    private val clock: Clock = Clock.System,
) : UserFeedRepository {

    override fun observeUsers(): Flow<List<FeedUser>> = combine(
        createdUserDao.getAll(),
        userDao.getAll(),
        pendingSyncDao.observeUpdatePendingSyncs(),
        pendingSyncDao.observeDeletePendingSyncs(),
    ) { created, cached, pendingUpdates, pendingDeletes ->
        val pendingEdits = pendingUpdates.associateBy { it.userId }
        // A created user can also be on the fetched page while GoRest has few users; show it once, as created.
        val hiddenIds = pendingDeletes.map { it.userId }.toSet() + created.mapNotNull { it.remoteId }
        created.map { it.toFeedUser() } +
            cached.filterNot { it.id in hiddenIds }.map { it.toFeedUser(pendingEdits[it.id]) }
    }

    override suspend fun createUser(name: String, email: String): EmptyResult<DataError.Network> {
        val localId = createdUserDao.upsert(
            CreatedUserEntity(name = name, email = email, createdAt = clock.now().toEpochMilliseconds()),
        )
        return userFeedApi.createUser(name, email)
            .onSuccess { created -> createdUserDao.setRemoteId(localId, created.id) }
            .onFailure { pendingSyncDao.upsertCreatePendingSync(CreateUserPendingSyncEntity(localId)) }
            .asEmptyResult()
    }

    override suspend fun updateUser(id: FeedUserId, name: String, email: String): EmptyResult<DataError.Network> {
        return when (id) {
            is FeedUserId.Created -> {
                // Already deleted: nothing left to edit.
                val user = createdUserDao.getByLocalId(id.localId) ?: return Result.Success(Unit)
                createdUserDao.updateDetails(id.localId, name, email)
                val remoteId = user.remoteId ?: return Result.Success(Unit)
                sendUpdate(remoteId, name, email)
            }
            is FeedUserId.Remote -> {
                userDao.update(UserEntity(id = id.id, name = name, email = email))
                sendUpdate(id.id, name, email)
            }
        }
    }

    override suspend fun deleteUser(id: FeedUserId): EmptyResult<DataError.Network> {
        return when (id) {
            is FeedUserId.Created -> {
                val user = createdUserDao.getByLocalId(id.localId) ?: return Result.Success(Unit)
                createdUserDao.deleteByLocalId(id.localId)
                val remoteId = user.remoteId
                if (remoteId == null) {
                    // Never reached the server, so dropping the pending create is the whole delete.
                    pendingSyncDao.deleteCreatePendingSync(id.localId)
                    Result.Success(Unit)
                } else {
                    sendDelete(remoteId)
                }
            }
            is FeedUserId.Remote -> {
                userDao.deleteById(id.id)
                sendDelete(id.id)
            }
        }
    }

    private suspend fun sendUpdate(remoteId: Long, name: String, email: String): EmptyResult<DataError.Network> =
        userFeedApi.updateUser(remoteId, name, email)
            // An older queued edit would overwrite this one when it syncs.
            .onSuccess { pendingSyncDao.deleteUpdatePendingSync(remoteId) }
            .onFailure {
                pendingSyncDao.upsertUpdatePendingSync(
                    UpdateUserPendingSyncEntity(
                        userId = remoteId,
                        name = name,
                        email = email,
                        updatedAt = clock.now().toEpochMilliseconds(),
                    ),
                )
            }
            .asEmptyResult()

    private suspend fun sendDelete(remoteId: Long): EmptyResult<DataError.Network> {
        // Whether or not the delete goes through, a queued edit of this user must never be sent.
        pendingSyncDao.deleteUpdatePendingSync(remoteId)
        return userFeedApi.deleteUser(remoteId)
            .onFailure { pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(remoteId)) }
    }
}
