package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants

/**
 * A created user GoRest refused (422, e.g. "email has already been taken"), so its [CreatedUserEntity] was removed.
 * Kept until the UI has told the user, since the rejection can arrive from a background sync while the app is closed.
 */
@Entity(tableName = DatabaseConstants.REJECTED_CREATIONS_TABLE)
data class RejectedCreationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
)
