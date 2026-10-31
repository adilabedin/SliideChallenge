package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.database.DatabaseTransactionRunner
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
import com.androidlearning.sliide_challenge.userfeed.domain.PendingUserChange
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.domain.UserSyncScheduler
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Room is the single source of truth. Each change is saved locally as pending before it is sent to GoRest, and stays
 * pending until GoRest accepts it. So a change that has been saved is never lost, even if the app dies mid-request:
 * [syncPendingChanges] sends whatever is left.
 *
 * A created user is pending while it has no GoRest id, so a create needs no queue entry of its own. An edit or delete
 * of a GoRest user is written together with its entry in the pending-sync tables, in one transaction. Changes to a
 * created user that has not been POSTed stay local, since its create will carry them.
 *
 * Whenever something new is pending, the repository asks [userSyncScheduler] for the periodic background sync, so the
 * change is sent later even if the request made now never finishes. That happens after the change is saved, not when
 * a request fails, because a request cut off by the app dying never reports a failure. A request that does fail also
 * gets a retry of its own ([UserSyncScheduler.scheduleChangeSync]), so one change's retries never wait on the others.
 */
class OfflineFirstUserFeedRepository(
    private val userFeedApi: UserFeedApi,
    private val userDao: UserDao,
    private val createdUserDao: CreatedUserDao,
    private val pendingSyncDao: UserPendingSyncDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val userSyncScheduler: UserSyncScheduler,
    private val applicationScope: ApplicationScope,
    private val clock: Clock = Clock.System
) : UserFeedRepository {

    /**
     * One lock per user (keyed by [FeedUserId]), held for each send of that user's change. So a save, a per-change
     * retry and the periodic sync never send the same change twice, and an older edit can never reach GoRest after a
     * newer one, while changes to different users go out without waiting for each other.
     */
    private val userLocks = KeyedMutex<FeedUserId>()

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
        // Without a GoRest id the row is already the pending create, so it can't be lost from here on.
        val localId = createdUserDao.upsert(CreatedUserEntity(name = name, email = email, createdAt = nowMillis()))
        userSyncScheduler.scheduleSync()
        send(PendingUserChange.Create(localId))
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
            if (remoteId == null) return@outliveCaller Result.Success(Unit)
            userSyncScheduler.scheduleSync()
            send(PendingUserChange.Update(remoteId))
        }

    override suspend fun deleteUser(id: FeedUserId): EmptyResult<DataError.Network> = outliveCaller {
        // The GoRest id to DELETE, or null when the user never reached GoRest.
        val remoteId = transactionRunner.runInTransaction {
            when (id) {
                is FeedUserId.Created -> {
                    val remoteId = createdUserDao.getByLocalId(id.localId)?.remoteId
                    // If it was never POSTed, removing the row (its pending create) is the whole delete.
                    createdUserDao.deleteByLocalId(id.localId)
                    remoteId?.also { queueDelete(it) }
                }

                is FeedUserId.Remote -> {
                    userDao.deleteById(id.id)
                    id.id.also { queueDelete(it) }
                }
            }
        }
        if (remoteId == null) return@outliveCaller Result.Success(Unit)
        userSyncScheduler.scheduleSync()
        send(PendingUserChange.Delete(remoteId))
    }

    override fun observeHasPendingSyncs(): Flow<Boolean> = pendingSyncDao.observeHasPendingSyncs()

    override suspend fun syncPendingChanges(): EmptyResult<DataError.Network> {
        // Creates go first: one created and then edited or deleted during its POST queues that follow-up below.
        val errors =
            createdUserDao.getUnsynced().mapNotNull { syncLocked(PendingUserChange.Create(it.localId)) } +
                pendingSyncDao.getUpdatePendingSyncs().mapNotNull { syncLocked(PendingUserChange.Update(it.userId)) } +
                pendingSyncDao.getDeletePendingSyncs().mapNotNull { syncLocked(PendingUserChange.Delete(it.userId)) }
        return errors.firstOrNull()?.let { Result.Error(it) } ?: Result.Success(Unit)
    }

    override suspend fun syncChange(change: PendingUserChange): EmptyResult<DataError.Network> {
        val error = syncLocked(change)
        return if (error != null && isPending(change)) Result.Error(error) else Result.Success(Unit)
    }

    /**
     * A change the user has saved is written and sent even if the screen that asked for it closes meanwhile; the
     * caller still gets the outcome if it is around to wait for it.
     */
    private suspend fun outliveCaller(
        block: suspend () -> EmptyResult<DataError.Network>
    ): EmptyResult<DataError.Network> = applicationScope.async { block() }.await()

    /**
     * Sends a change the user just saved, after any send of the same user's changes already running. If it fails but is
     * still pending (not rejected), it gets a retry of its own.
     */
    private suspend fun send(change: PendingUserChange): EmptyResult<DataError.Network> {
        val error = syncLocked(change) ?: return Result.Success(Unit)
        if (isPending(change)) userSyncScheduler.scheduleChangeSync(change)
        return Result.Error(error)
    }

    private suspend fun syncLocked(change: PendingUserChange): DataError.Network? = userLocks.withLock(change.user) {
        when (change) {
            is PendingUserChange.Create -> syncCreate(change.localId)
            is PendingUserChange.Update -> syncUpdate(change.userId)
            is PendingUserChange.Delete -> syncDelete(change.userId)
        }
    }

    private suspend fun isPending(change: PendingUserChange): Boolean = when (change) {
        is PendingUserChange.Create -> createdUserDao.getByLocalId(change.localId)?.let { it.remoteId == null } == true
        is PendingUserChange.Update -> pendingSyncDao.getUpdatePendingSync(change.userId) != null
        is PendingUserChange.Delete -> pendingSyncDao.getDeletePendingSync(change.userId) != null
    }

    /** The user whose lock a change takes: a create by its local id, an edit or delete by its GoRest id. */
    private val PendingUserChange.user: FeedUserId
        get() = when (this) {
            is PendingUserChange.Create -> FeedUserId.Created(localId)
            is PendingUserChange.Update -> FeedUserId.Remote(userId)
            is PendingUserChange.Delete -> FeedUserId.Remote(userId)
        }

    /*
     * Each sync step runs under its user's lock and re-reads what it sends (the created row, or the queue entry), since
     * another send may already have handled it. It returns the error its request failed with, or null once the change
     * is no longer pending.
     */

    private suspend fun syncCreate(localId: Long): DataError.Network? {
        // Gone if deleted meanwhile; a GoRest id means a sync that ran meanwhile already created it.
        val user = createdUserDao.getByLocalId(localId)?.takeIf { it.remoteId == null } ?: return null
        createdUserDao.markSent(localId)
        val created = when (val result = userFeedApi.createUser(user.name, user.email)) {
            is Result.Success -> result.data.copy(name = user.name, email = user.email)

            is Result.Error -> {
                if (result.error != DataError.Network.UNPROCESSABLE_ENTITY) return result.error
                val lost = if (user.wasSent) {
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
     * is compared against what GoRest now holds ([created]), and any difference is queued.
     */
    private suspend fun recordCreated(localId: Long, created: UserDto) {
        val followUp = transactionRunner.runInTransaction {
            createdUserDao.setRemoteId(localId, created.id, nowMillis())
            val latest = createdUserDao.getByLocalId(localId)
            if (latest == null) {
                queueDelete(created.id)
                PendingUserChange.Delete(created.id)
            } else if (latest.name != created.name || latest.email != created.email) {
                queueUpdate(created.id, latest.name, latest.email)
                PendingUserChange.Update(created.id)
            } else {
                null
            }
        }
        // It takes the GoRest id's lock, not this create's, so it is sent by its own run (or by a running sync, where
        // edits and deletes go after creates) rather than from inside this one.
        if (followUp != null) {
            userSyncScheduler.scheduleSync()
            userSyncScheduler.scheduleChangeSync(followUp)
        }
    }

    /**
     * GoRest will never accept this user (422), so retrying is pointless: drop the row, which is its pending create.
     * It could never sync, and the form's corrected resubmit would otherwise add a second copy.
     */
    private suspend fun rejectCreation(localId: Long) {
        createdUserDao.deleteByLocalId(localId)
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
