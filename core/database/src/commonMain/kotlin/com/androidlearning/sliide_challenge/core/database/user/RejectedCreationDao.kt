package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants
import kotlinx.coroutines.flow.Flow

@Dao
interface RejectedCreationDao {
    @Insert
    suspend fun insert(item: RejectedCreationEntity)

    /** Oldest first, so rejections are reported in the order they happened. */
    @Query("SELECT * FROM ${DatabaseConstants.REJECTED_CREATIONS_TABLE} ORDER BY id")
    fun observeAll(): Flow<List<RejectedCreationEntity>>

    @Query("DELETE FROM ${DatabaseConstants.REJECTED_CREATIONS_TABLE} WHERE id = :id")
    suspend fun deleteById(id: Long)
}
