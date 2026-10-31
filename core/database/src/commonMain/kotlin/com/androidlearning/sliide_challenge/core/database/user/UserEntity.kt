package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants

/**
 * Cached GoRest user. [id] is the server id, so it is not auto-generated.
 * [lastSyncedAt] is epoch milliseconds of the last time this row matched the server (a refresh or a successful PATCH);
 * GoRest has no timestamps of its own, so this is what the feed's relative timestamp shows.
 */
@Entity(tableName = DatabaseConstants.USERS_TABLE)
data class UserEntity(@PrimaryKey val id: Long, val name: String, val email: String, val lastSyncedAt: Long)
