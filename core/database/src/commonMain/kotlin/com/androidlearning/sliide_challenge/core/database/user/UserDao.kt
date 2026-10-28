package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    /** Existing rows with the same id are replaced, so re-fetching a page refreshes the cache. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)

    /**
     * Writes an edit of a cached user without touching [UserEntity.lastSyncedAt]; does nothing if a refresh has already
     * dropped the row.
     */
    @Query("UPDATE ${DatabaseConstants.USERS_TABLE} SET name = :name, email = :email WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, email: String)

    @Query("UPDATE ${DatabaseConstants.USERS_TABLE} SET lastSyncedAt = :syncedAt WHERE id = :id")
    suspend fun setLastSyncedAt(id: Long, syncedAt: Long)

    @Query("DELETE FROM ${DatabaseConstants.USERS_TABLE} WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ${DatabaseConstants.USERS_TABLE}")
    suspend fun deleteAll()

    /** Atomically swaps the cache for [users], so observers never see an empty table in between. */
    @Transaction
    suspend fun replaceAll(users: List<UserEntity>) {
        deleteAll()
        insertAll(users)
    }

    /** Newest (highest GoRest id) first. */
    @Query("SELECT * FROM ${DatabaseConstants.USERS_TABLE} ORDER BY id DESC")
    fun getAll(): Flow<List<UserEntity>>
}
