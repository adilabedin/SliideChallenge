package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants
import kotlinx.coroutines.flow.Flow

/** Queues of local changes still to be sent to GoRest. A row is removed only once its request has succeeded. */
@Dao
interface UserPendingSyncDao {
    @Upsert
    suspend fun upsertCreatePendingSync(item: CreateUserPendingSyncEntity)

    @Query("SELECT * FROM ${DatabaseConstants.CREATE_USER_PENDING_SYNCS_TABLE}")
    suspend fun getCreatePendingSyncs(): List<CreateUserPendingSyncEntity>

    @Query("DELETE FROM ${DatabaseConstants.CREATE_USER_PENDING_SYNCS_TABLE} WHERE localId = :localId")
    suspend fun deleteCreatePendingSync(localId: Long)

    /** Replaces any earlier pending edit of the same user, so only the latest one is sent. */
    @Upsert
    suspend fun upsertUpdatePendingSync(item: UpdateUserPendingSyncEntity)

    @Query("SELECT * FROM ${DatabaseConstants.UPDATE_USER_PENDING_SYNCS_TABLE}")
    suspend fun getUpdatePendingSyncs(): List<UpdateUserPendingSyncEntity>

    /** Lets the feed overlay unsynced edits on server rows, which a refresh would otherwise overwrite. */
    @Query("SELECT * FROM ${DatabaseConstants.UPDATE_USER_PENDING_SYNCS_TABLE}")
    fun observeUpdatePendingSyncs(): Flow<List<UpdateUserPendingSyncEntity>>

    @Query("DELETE FROM ${DatabaseConstants.UPDATE_USER_PENDING_SYNCS_TABLE} WHERE userId = :userId")
    suspend fun deleteUpdatePendingSync(userId: Long)

    /** Removes the edit only if it is still the one that was sent, so a newer edit queued meanwhile is kept. */
    @Query(
        "DELETE FROM ${DatabaseConstants.UPDATE_USER_PENDING_SYNCS_TABLE} WHERE userId = :userId AND updatedAt = :updatedAt",
    )
    suspend fun deleteUpdatePendingSyncIfUnchanged(userId: Long, updatedAt: Long)

    @Upsert
    suspend fun upsertDeletePendingSync(item: DeleteUserPendingSyncEntity)

    @Query("SELECT * FROM ${DatabaseConstants.DELETE_USER_PENDING_SYNCS_TABLE}")
    suspend fun getDeletePendingSyncs(): List<DeleteUserPendingSyncEntity>

    /** Lets the feed hide users deleted offline, which a refresh would otherwise bring back. */
    @Query("SELECT * FROM ${DatabaseConstants.DELETE_USER_PENDING_SYNCS_TABLE}")
    fun observeDeletePendingSyncs(): Flow<List<DeleteUserPendingSyncEntity>>

    @Query("DELETE FROM ${DatabaseConstants.DELETE_USER_PENDING_SYNCS_TABLE} WHERE userId = :userId")
    suspend fun deleteDeletePendingSync(userId: Long)

    /** True while any queue has an entry, so background sync only needs scheduling then. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM ${DatabaseConstants.CREATE_USER_PENDING_SYNCS_TABLE}) " +
            "OR EXISTS(SELECT 1 FROM ${DatabaseConstants.UPDATE_USER_PENDING_SYNCS_TABLE}) " +
            "OR EXISTS(SELECT 1 FROM ${DatabaseConstants.DELETE_USER_PENDING_SYNCS_TABLE})",
    )
    fun observeHasPendingSyncs(): Flow<Boolean>
}
