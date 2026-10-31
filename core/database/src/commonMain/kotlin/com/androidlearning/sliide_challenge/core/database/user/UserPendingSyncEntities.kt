package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants

/**
 * Latest unsynced edit of a server user, keyed by its GoRest id; upserting keeps only the newest edit.
 * Edits to a not-yet-synced create never land here (they update [CreatedUserEntity] in place).
 * Removed once the PATCH succeeds.
 */
@Entity(tableName = DatabaseConstants.UPDATE_USER_PENDING_SYNCS_TABLE)
data class UpdateUserPendingSyncEntity(
    @PrimaryKey val userId: Long,
    val name: String,
    val email: String,
    val updatedAt: Long
)

/**
 * A server user deleted locally whose DELETE has not been sent yet, keyed by its GoRest id.
 * Deleting a not-yet-synced create never lands here (the created row is removed instead).
 * Removed once the server returns 204.
 */
@Entity(tableName = DatabaseConstants.DELETE_USER_PENDING_SYNCS_TABLE)
data class DeleteUserPendingSyncEntity(@PrimaryKey val userId: Long)
