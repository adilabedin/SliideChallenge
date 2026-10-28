package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.database.user.CreatedUserEntity
import com.androidlearning.sliide_challenge.core.database.user.UpdateUserPendingSyncEntity
import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId

fun CreatedUserEntity.toFeedUser(): FeedUser = FeedUser(
    id = FeedUserId.Created(localId),
    name = name,
    email = email,
)

/** [pendingEdit] wins over the cached values, since a refresh may have overwritten the row with the server's copy. */
fun UserEntity.toFeedUser(pendingEdit: UpdateUserPendingSyncEntity? = null): FeedUser = FeedUser(
    id = FeedUserId.Remote(id),
    name = pendingEdit?.name ?: name,
    email = pendingEdit?.email ?: email,
)
