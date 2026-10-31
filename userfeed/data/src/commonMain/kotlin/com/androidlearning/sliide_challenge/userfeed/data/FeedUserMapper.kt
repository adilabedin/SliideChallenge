package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.database.user.CreatedUserEntity
import com.androidlearning.sliide_challenge.core.database.user.UpdateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import kotlin.time.Instant

/**
 * Unsynced while its create has not been POSTed or [pendingEdit] is queued. The edit itself is already on the row,
 * which is updated in place.
 */
fun CreatedUserEntity.toFeedUser(pendingEdit: UpdateUserPendingSyncEntity? = null): FeedUser = FeedUser(
    id = FeedUserId.Created(localId),
    name = name,
    email = email,
    lastSyncedAt = lastSyncedAt?.takeIf {
        remoteId != null && pendingEdit == null
    }?.let(Instant::fromEpochMilliseconds)
)

/**
 * [pendingEdit] wins over the cached values, since a refresh may have overwritten the row with the server's copy,
 * and marks the row unsynced.
 */
fun UserEntity.toFeedUser(pendingEdit: UpdateUserPendingSyncEntity? = null): FeedUser = FeedUser(
    id = FeedUserId.Remote(id),
    name = pendingEdit?.name ?: name,
    email = pendingEdit?.email ?: email,
    lastSyncedAt = if (pendingEdit == null) Instant.fromEpochMilliseconds(lastSyncedAt) else null
)
