package com.androidlearning.sliide_challenge.core.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.androidlearning.sliide_challenge.core.database.DatabaseConstants

/**
 * Permanent record of a user created on this device. GoRest puts new users on page 1, never on the last page we
 * fetch, so this table is the only place they can be read back from; it is never cleared by a refresh or a sync.
 *
 * [remoteId] is null until GoRest has accepted the create. A row without one is itself the queued create: there is no
 * separate queue entry, so a create saved here can't be lost even if the app dies before or during its POST.
 * [wasSent] is set just before each POST, so a later POST knows an earlier one may have reached GoRest even though
 * its 201 never arrived (a timeout, or the app dying mid-request).
 * [createdAt] is epoch milliseconds, used for ordering.
 * [lastSyncedAt] is epoch milliseconds of the last time GoRest accepted this user (its POST or a later PATCH); null
 * until the create has been POSTed.
 */
@Entity(tableName = DatabaseConstants.CREATED_USERS_TABLE)
data class CreatedUserEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Long? = null,
    val name: String,
    val email: String,
    val createdAt: Long,
    val lastSyncedAt: Long? = null,
    val wasSent: Boolean = false
)
