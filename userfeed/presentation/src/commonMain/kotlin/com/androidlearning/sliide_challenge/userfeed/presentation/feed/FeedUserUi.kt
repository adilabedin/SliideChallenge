package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import kotlin.time.Instant

data class FeedUserUi(
    val id: FeedUserId,
    val name: String,
    val email: String,
    /** e.g. "Synced 5 minutes ago" or "Not synced". */
    val syncStatus: String,
    val isSynced: Boolean,
)

fun FeedUser.toFeedUserUi(now: Instant): FeedUserUi = FeedUserUi(
    id = id,
    name = name,
    email = email,
    syncStatus = formatSyncStatus(lastSyncedAt, now),
    isSynced = lastSyncedAt != null,
)
