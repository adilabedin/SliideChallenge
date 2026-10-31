package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.database.DatabaseTransactionRunner
import com.androidlearning.sliide_challenge.core.database.user.CreateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserDao
import com.androidlearning.sliide_challenge.core.database.user.CreatedUserEntity
import com.androidlearning.sliide_challenge.core.database.user.DeleteUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UpdateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UserDao
import com.androidlearning.sliide_challenge.core.database.user.UserPendingSyncDao
import com.androidlearning.sliide_challenge.core.domain.util.ApplicationScope
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.core.domain.util.asEmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.map
import com.androidlearning.sliide_challenge.core.domain.util.onSuccess
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room is the single source of truth. Each change is written locally together with its entry in the pending-sync
 * tables, in one transaction, and only then sent to GoRest; the entry is removed once GoRest accepts it. So a change
 * that has been saved is never lost, even if the app dies mid-request: [syncPendingChanges] sends whatever is left.
 * Changes to a created user that has not been POSTed stay local, since its pending create will carry them.
 */
class OfflineFirstUserFeedRepository(
    private val userFeedApi: UserFeedApi,
    private val userDao: UserDao,
    private val createdUserDao: CreatedUserDao,
    private val pendingSyncDao: UserPendingSyncDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val applicationScope: ApplicationScope,
    private val clock: Clock = Clock.System
) : UserFeedRepository {

    /**
     * Requests are sent one at a time, so a background sync and a change the user just made never send the same
     * queued entry twice, and an older edit can never reach GoRest after a newer one.
     */
    private val syncMutex = Mutex()

    override fun observeUsers(): Flow<List<FeedUser>> = combine(
        createdUserDao.getAll(),
        userDao.getAll(),
        pendingSyncDao.observeUpdatePendingSyncs(),
        pendingSyncDao.observeDeletePendingSyncs()
    ) { created, cached, pendingUpdates, pendingDeletes ->
        val pendingEdits = pendingUpdates.associateBy { it.userId }
        // A created user can also be on the fetched page while GoRest has few users; show it once, as created.
        val hiddenIds = pendingDeletes.map { it.userId }.toSet() + created.mapNotNull { it.remoteId }
        created.map { it.toFeedUser(it.remoteId?.let(pendingEdits::get)) } +
            cached.filterNot { it.id in hiddenIds }.map { it.toFeedUser(pendingEdits[it.id]) }
    }

    override suspend fun createUser(name: String, email: String): EmptyResult<DataError.Network> = outliveCaller {
        val localId = transactionRunner.runInTransaction {
            createdUserDao.upsert(CreatedUserEntity(name = name, email = email, createdAt = nowMillis()))
                .also { pendingSyncDao.upsertCreatePendingSync(CreateUserPendingSyncEntity(it)) }
        }
        send { syncCreate(localId) }
    }

    override suspend fun loadUsers(): EmptyResult<DataError.Network> {
        // Sent first so the fetched page already reflects them; whatever fails stays queued, so only the fetch's
        // outcome is reported.
        syncPendingChanges()
        return loadLastPage()
    }

    override suspend fun loadUsersIfNotCached(): EmptyResult<DataError.Network> =
        if (userDao.getAll().first().isEmpty()) loadUsers() else Result.Success(Unit)

    /** Replaces the cached page with the last GoRest page, stamping each row with the fetch time. */
    private suspend fun loadLastPage(): EmptyResult<DataError.Network> = userFeedApi.getLastPageUsers()
        .map { users ->
            val syncedAt = nowMillis()
            users.map { it.toEntity(syncedAt) }
        }
        .onSuccess { userDao.replaceAll(it) }
        .asEmptyResult()

    override suspend fun updateUser(id: FeedUserId, name: String, email: String): EmptyResult<DataError.Network> =
        outliveCaller {
            // The GoRest id to PATCH, or null when there is nothing to send.
            val remoteId = transactionRunner.runInTransaction {
                when (id) {
                    is FeedUserId.Created -> {
                        // Null if already deleted, or not POSTed yet: then its pending create carries the edit.
                        val remoteId = createdUserDao.getByLocalId(id.localId)?.remoteId
                        createdUserDao.updateDetails(id.localId, name, email)
                        remoteId?.also { queueUpdate(it, name, email) }
                    }

                    is FeedUserId.Remote -> {
                        userDao.updateDetails(id.id, name, email)
                        id.id.also { queueUpdate(it, name, email) }
                    }
                }
            }
            if (remoteId == null) Result.Success(Unit) else send { syncUpdate(remoteId) }
        }

    override suspend fun deleteUser(id: FeedUserId): EmptyResult<DataError.Network> = outliveCaller {
        // The GoRest id to DELETE, or null when the user never reached GoRest.
        val remoteId = transactionRunner.runInTransaction {
            when (id) {
                is FeedUserId.Created -> {
                    val remoteId = createdUserDao.getByLocalId(id.localId)?.remoteId
                    createdUserDao.deleteByLocalId(id.localId)
                    // If it was never POSTed, dropping its pending create is the whole delete.
                    pendingSyncDao.deleteCreatePendingSync(id.localId)
                    remoteId?.also { queueDelete(it) }
                }

                is FeedUserId.Remote -> {
                    userDao.deleteById(id.id)
                    id.id.also { queueDelete(it) }
                }
            }
        }
        if (remoteId == null) Result.Success(Unit) else send { syncDelete(remoteId) }
    }

    override fun observeHasPendingSyncs(): Flow<Boolean> = pendingSyncDao.observeHasPendingSyncs()

    override suspend fun syncPendingChanges(): EmptyResult<DataError.Network> = syncMutex.withLock {
        // Creates go first: one created and then edited or deleted during its POST queues that follow-up below.
        val errors = pendingSyncDao.getCreatePendingSyncs().mapNotNull { syncCreate(it.localId) } +
            pendingSyncDao.getUpdatePendingSyncs().mapNotNull { syncUpdate(it.userId) } +
            pendingSyncDao.getDeletePendingSyncs().mapNotNull { syncDelete(it.userId) }
        errors.firstOrNull()?.let { Result.Error(it) } ?: Result.Success(Unit)
    }

    /**
     * A change the user has saved is written and sent even if the screen that asked for it closes meanwhile; the
     * caller still gets the outcome if it is around to wait for it.
     */
    private suspend fun outliveCaller(
        block: suspend () -> EmptyResult<DataError.Network>
    ): EmptyResult<DataError.Network> = applicationScope.async { block() }.await()

    /** Sends one queued change right away, in turn with any sync already running. */
    private suspend fun send(sync: suspend () -> DataError.Network?): EmptyResult<DataError.Network> =
        syncMutex.withLock { sync() }?.let { Result.Error(it) } ?: Result.Success(Unit)

    /*
     * Each sync step re-reads its queue entry, since a sync that ran meanwhile may already have sent it, and returns
     * the error its request failed with, or null once the change is no longer pending.
     */

    private suspend fun syncCreate(localId: Long): DataError.Network? {
        val pending = pendingSyncDao.getCreatePendingSync(localId) ?: return null
        val user = createdUserDao.getByLocalId(localId)
        if (user == null) {
            pendingSyncDao.deleteCreatePendingSync(localId)
            return null
        }
        pendingSyncDao.markCreatePendingSyncSent(localId)
        val created = when (val result = userFeedApi.createUser(user.name, user.email)) {
            is Result.Success -> result.data.copy(name = user.name, email = user.email)

            is Result.Error -> {
                if (result.error != DataError.Network.UNPROCESSABLE_ENTITY) return result.error
                val lost = if (pending.wasSent) {
                    when (val lookup = findLostCreate(user)) {
                        is Result.Error -> return lookup.error
                        is Result.Success -> lookup.data
                    }
                } else {
                    null
                }
                if (lost == null) {
                    rejectCreation(localId)
                    return result.error
                }
                lost
            }
        }
        recordCreated(localId, created)
        return null
    }

    /**
     * An earlier POST of [user] may have reached GoRest with its 201 lost, so the resend is refused because the email
     * is now taken. That user is ours if it has the same name too; otherwise someone else holds the email.
     */
    private suspend fun findLostCreate(user: CreatedUserEntity): Result<UserDto?, DataError.Network> =
        when (val result = userFeedApi.findUserByEmail(user.email)) {
            is Result.Error -> result
            is Result.Success -> Result.Success(result.data?.takeIf { it.name == user.name })
        }

    /**
     * From here on, edits and deletes send their own requests. Ones made during the POST only touched the row, so it
     * is compared against what GoRest now holds ([created]).
     */
    private suspend fun recordCreated(localId: Long, created: UserDto) {
        transactionRunner.runInTransaction {
            createdUserDao.setRemoteId(localId, created.id, nowMillis())
            val latest = createdUserDao.getByLocalId(localId)
            if (latest == null) {
                queueDelete(created.id)
            } else if (latest.name != created.name || latest.email != created.email) {
                queueUpdate(created.id, latest.name, latest.email)
            }
            pendingSyncDao.deleteCreatePendingSync(localId)
        }
    }

    /**
     * GoRest will never accept this user (422), so retrying is pointless: drop it with its pending create. The local
     * row goes too, since it could never sync and the form's corrected resubmit would otherwise add a second copy.
     */
    private suspend fun rejectCreation(localId: Long) {
        transactionRunner.runInTransaction {
            createdUserDao.deleteByLocalId(localId)
            pendingSyncDao.deleteCreatePendingSync(localId)
        }
    }

    private suspend fun syncUpdate(userId: Long): DataError.Network? {
        // Read now, so the latest edit is the one sent.
        val edit = pendingSyncDao.getUpdatePendingSync(userId) ?: return null
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
        pendingSyncDao.getDeletePendingSync(userId) ?: return null
        val result = userFeedApi.deleteUser(userId)
        // NOT_FOUND means the user is already gone, which is what the delete wanted.
        if (result is Result.Error && result.error != DataError.Network.NOT_FOUND) return result.error
        pendingSyncDao.deleteDeletePendingSync(userId)
        return null
    }

    /** Replaces any older queued edit of the user, so only the latest one is sent. */
    private suspend fun queueUpdate(remoteId: Long, name: String, email: String) {
        pendingSyncDao.upsertUpdatePendingSync(
            UpdateUserPendingSyncEntity(userId = remoteId, name = name, email = email, updatedAt = nowMillis())
        )
    }

    private suspend fun queueDelete(remoteId: Long) {
        // A queued edit of a deleted user must never be sent.
        pendingSyncDao.deleteUpdatePendingSync(remoteId)
        pendingSyncDao.upsertDeletePendingSync(DeleteUserPendingSyncEntity(remoteId))
    }

    /** A user is addressed by its GoRest id in either table, so stamp whichever holds it. */
    private suspend fun markSynced(remoteId: Long) {
        val now = nowMillis()
        userDao.setLastSyncedAt(remoteId, now)
        createdUserDao.setLastSyncedAt(remoteId, now)
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()
}
