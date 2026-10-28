package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants

/** Cached GoRest user. [id] is the server id, so it is not auto-generated. */
@Entity(tableName = DatabaseConstants.USERS_TABLE)
data class UserEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val email: String,
)
