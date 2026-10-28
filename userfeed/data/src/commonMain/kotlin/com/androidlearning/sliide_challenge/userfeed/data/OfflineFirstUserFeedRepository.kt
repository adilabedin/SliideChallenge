package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.database.user.CreateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserDao
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserEntity
import com.androidlearning.sliide_challenge.core.database.user.DeleteUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.RejectedCreationDao
import com.androidlearning.sliide_challenge.core.database.user.RejectedCreationEntity
import com.androidlearning.sliide_challenge.core.database.user.UpdateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UserDao
import com.androidlearning.sliide_challenge.core.database.user.UserPendingSyncDao
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.core.domain.util.asEmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.onFailure
import com.androidlearning.sliide_challenge.core.domain.util.onSuccess
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.RejectedCreation
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Room is the single source of truth. Each change is written locally first, then sent to GoRest; a failed request is
 * queued in the pending-sync tables, which [syncPendingChanges] drains. Changes to a created user that has not been
 * POSTed stay local, since its pending create will carry them.
 */
class OfflineFirstUserFeedRepository(
    private val userFeedApi: UserFeedApi,
    private val userDao: UserDao,
    private val createdUserDao: CreatedUserDao,
    private val pendingSyncDao: UserPendingSyncDao,
    private val rejectedCreationDao: RejectedCreationDao,
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
        created.map { it.toFeedUser(it.remoteId?.let(pendingEdits::get)) } +
            cached.filterNot { it.id in hiddenIds }.map { it.toFeedUser(pendingEdits[it.id]) }
    }

    override suspend fun createUser(name: String, email: String): EmptyResult<DataError.Network> {
        val localId = createdUserDao.upsert(
            CreatedUserEntity(name = name, email = email, createdAt = nowMillis()),
        )
        return userFeedApi.createUser(name, email)
            .onSuccess { created -> createdUserDao.setRemoteId(localId, created.id, nowMillis()) }
            .onFailure { error ->
                if (error == DataError.Network.UNPROCESSABLE_ENTITY) {
                    rejectCreation(localId, name, email)
                } else {
                    pendingSyncDao.upsertCreatePendingSync(CreateUserPendingSyncEntity(localId))
                }
            }
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
                userDao.updateDetails(id.id, name, email)
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

    override fun observeRejectedCreations(): Flow<List<RejectedCreation>> =
        rejectedCreationDao.observeAll().map { rows -> rows.map { RejectedCreation(it.id, it.name, it.email) } }

    override suspend fun dismissRejectedCreation(id: Long) {
        rejectedCreationDao.deleteById(id)
    }

    /** GoRest will never accept this user (422), so retrying is pointless: drop it and keep a record for the UI. */
    private suspend fun rejectCreation(localId: Long, name: String, email: String) {
        createdUserDao.deleteByLocalId(localId)
        rejectedCreationDao.insert(RejectedCreationEntity(name = name, email = email))
    }

    override fun observeHasPendingSyncs(): Flow<Boolean> = pendingSyncDao.observeHasPendingSyncs()

    /** Background and foreground syncs can overlap (iOS); without this, both would send the same queued changes. */
    private val syncMutex = Mutex()

    override suspend fun syncPendingChanges(): EmptyResult<DataError.Network> = syncMutex.withLock {
        // Creates go first: one created and then edited or deleted during its POST queues that follow-up below.
        val errors = pendingSyncDao.getCreatePendingSyncs().mapNotNull { syncCreate(it.localId) } +
            pendingSyncDao.getUpdatePendingSyncs().mapNotNull { syncUpdate(it) } +
            pendingSyncDao.getDeletePendingSyncs().mapNotNull { syncDelete(it.userId) }
        errors.firstOrNull()?.let { Result.Error(it) } ?: Result.Success(Unit)
    }

    /** Each sync step returns the error that kept its change queued, or null once the change is no longer pending. */
    private suspend fun syncCreate(localId: Long): DataError.Network? {
        val user = createdUserDao.getByLocalId(localId)
        if (user == null) {
            pendingSyncDao.deleteCreatePendingSync(localId)
            return null
        }
        val remoteId = when (val result = userFeedApi.createUser(user.name, user.email)) {
            is Result.Error -> {
                if (result.error != DataError.Network.UNPROCESSABLE_ENTITY) return result.error
                rejectCreation(localId, user.name, user.email)
                pendingSyncDao.deleteCreatePendingSync(localId)
                return null
            }
            is Result.Success -> result.data.id
        }
        // From here on, edits and deletes send their own requests. Ones made during the POST only touched the row,
        // so compare it against what was sent.
        createdUserDao.setRemoteId(localId, remoteId, nowMillis())
        val latest = createdUserDao.getByLocalId(localId)
        if (latest == null) {
            pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(remoteId))
        } else if (latest.name != user.name || latest.email != user.email) {
            pendingSyncDao.upsertUpdatePendingSync(
                UpdateUserPendingSyncEntity(
                    userId = remoteId,
                    name = latest.name,
                    email = latest.email,
                    updatedAt = nowMillis(),
                ),
            )
        }
        // Removed last so the queues are never briefly empty while a follow-up is still to be queued.
        pendingSyncDao.deleteCreatePendingSync(localId)
        return null
    }

    private suspend fun syncUpdate(edit: UpdateUserPendingSyncEntity): DataError.Network? {
        val error = when (val result = userFeedApi.updateUser(edit.userId, edit.name, edit.email)) {
            is Result.Success -> {
                userDao.updateDetails(edit.userId, edit.name, edit.email)
                markSynced(edit.userId)
                null
            }
            is Result.Error -> result.error
        }
        if (error != null && error != DataError.Network.NOT_FOUND) return error
        pendingSyncDao.deleteUpdatePendingSyncIfUnchanged(edit.userId, edit.updatedAt)
        return null
    }

    private suspend fun syncDelete(userId: Long): DataError.Network? {
        val result = userFeedApi.deleteUser(userId)
        // NOT_FOUND means the user is already gone, which is what the delete wanted.
        if (result is Result.Error && result.error != DataError.Network.NOT_FOUND) return result.error
        pendingSyncDao.deleteDeletePendingSync(userId)
        return null
    }

    private suspend fun sendUpdate(remoteId: Long, name: String, email: String): EmptyResult<DataError.Network> =
        userFeedApi.updateUser(remoteId, name, email)
            .onSuccess {
                markSynced(remoteId)
                // An older queued edit would overwrite this one when it syncs.
                pendingSyncDao.deleteUpdatePendingSync(remoteId)
            }
            .onFailure {
                pendingSyncDao.upsertUpdatePendingSync(
                    UpdateUserPendingSyncEntity(
                        userId = remoteId,
                        name = name,
                        email = email,
                        updatedAt = nowMillis(),
                    ),
                )
            }
            .asEmptyResult()

    /** A user is addressed by its GoRest id in either table, so stamp whichever holds it. */
    private suspend fun markSynced(remoteId: Long) {
        val now = nowMillis()
        userDao.setLastSyncedAt(remoteId, now)
        createdUserDao.setLastSyncedAt(remoteId, now)
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()

    private suspend fun sendDelete(remoteId: Long): EmptyResult<DataError.Network> {
        // Whether or not the delete goes through, a queued edit of this user must never be sent.
        pendingSyncDao.deleteUpdatePendingSync(remoteId)
        return userFeedApi.deleteUser(remoteId)
            .onFailure { pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(remoteId)) }
    }
}
