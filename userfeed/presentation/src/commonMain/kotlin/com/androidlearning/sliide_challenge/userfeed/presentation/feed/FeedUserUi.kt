package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import kotlin.time.Instant

data class FeedUserUi(
    val id: FeedUserId,
    val name: String,
    val email: String,
    val syncStatus: SyncStatus,
    val isSynced: Boolean
)

fun FeedUser.toFeedUserUi(now: Instant): FeedUserUi = FeedUserUi(
    id = id,
    name = name,
    email = email,
    syncStatus = syncStatusOf(lastSyncedAt, now),
    isSynced = lastSyncedAt != null
)

/** Lazy-list keys must be saveable on Android, so the sealed id is flattened to a string unique across both kinds. */
internal fun FeedUserId.listKey(): String = when (this) {
    is FeedUserId.Created -> "created-$localId"
    is FeedUserId.Remote -> "remote-$id"
}
