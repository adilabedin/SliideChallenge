package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants
import kotlinx.coroutines.flow.Flow

@Dao
interface CreatedUserDao {
    /** Returns the row id, which is the generated [CreatedUserEntity.localId] for a new user. */
    @Upsert
    suspend fun upsert(user: CreatedUserEntity): Long

    /** Newest creation first, so they can be shown above the fetched page. */
    @Query("SELECT * FROM ${DatabaseConstants.CREATED_USERS_TABLE} ORDER BY createdAt DESC, localId DESC")
    fun getAll(): Flow<List<CreatedUserEntity>>

    @Query("SELECT * FROM ${DatabaseConstants.CREATED_USERS_TABLE} WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): CreatedUserEntity?

    /** Edits only the user's fields, so a [setRemoteId] landing concurrently is never overwritten. */
    @Query("UPDATE ${DatabaseConstants.CREATED_USERS_TABLE} SET name = :name, email = :email WHERE localId = :localId")
    suspend fun updateDetails(localId: Long, name: String, email: String)

    /** Records the 201: the user now has a GoRest id and was in sync at [syncedAt]. */
    @Query(
        "UPDATE ${DatabaseConstants.CREATED_USERS_TABLE} SET remoteId = :remoteId, lastSyncedAt = :syncedAt " +
            "WHERE localId = :localId",
    )
    suspend fun setRemoteId(localId: Long, remoteId: Long, syncedAt: Long)

    /** Records a successful PATCH, which addresses the user by its GoRest id. */
    @Query("UPDATE ${DatabaseConstants.CREATED_USERS_TABLE} SET lastSyncedAt = :syncedAt WHERE remoteId = :remoteId")
    suspend fun setLastSyncedAt(remoteId: Long, syncedAt: Long)

    @Query("DELETE FROM ${DatabaseConstants.CREATED_USERS_TABLE} WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)
}
