package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants

/**
 * Permanent record of a user created on this device. GoRest puts new users on page 1, never on the last page we
 * fetch, so this table is the only place they can be read back from; it is never cleared by a refresh or a sync.
 *
 * [remoteId] is null until the create has been POSTed (see [CreateUserPendingSyncEntity]).
 * [createdAt] is epoch milliseconds, used for ordering and the relative timestamp.
 */
@Entity(tableName = DatabaseConstants.CREATED_USERS_TABLE)
data class CreatedUserEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Long? = null,
    val name: String,
    val email: String,
    val createdAt: Long,
)
