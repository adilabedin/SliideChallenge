package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.compose.runtime.Composable
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.sync_status_not_synced
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.sync_status_synced_days_ago
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.sync_status_synced_hours_ago
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.sync_status_synced_just_now
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.sync_status_synced_minutes_ago
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Whether a user's last change has reached the server, kept as data so the wording lives in string resources. */
sealed interface SyncStatus {
    /** A change is waiting to sync. */
    data object NotSynced : SyncStatus
    data class Synced(val ago: TimeAgo) : SyncStatus
}

sealed interface TimeAgo {
    data object JustNow : TimeAgo
    data class Minutes(val count: Int) : TimeAgo
    data class Hours(val count: Int) : TimeAgo
    data class Days(val count: Int) : TimeAgo
}

/** [SyncStatus.NotSynced] while the user has a change waiting to sync ([lastSyncedAt] is null). */
fun syncStatusOf(lastSyncedAt: Instant?, now: Instant): SyncStatus =
    if (lastSyncedAt == null) SyncStatus.NotSynced else SyncStatus.Synced(timeAgo(lastSyncedAt, now))

/** Largest whole unit, rounded down. A time after [now] (the device clock moved back) reads as just now. */
fun timeAgo(then: Instant, now: Instant): TimeAgo {
    val elapsed = now - then
    return when {
        elapsed < 1.minutes -> TimeAgo.JustNow
        elapsed < 1.hours -> TimeAgo.Minutes(elapsed.inWholeMinutes.toInt())
        elapsed < 1.days -> TimeAgo.Hours(elapsed.inWholeHours.toInt())
        else -> TimeAgo.Days(elapsed.inWholeDays.toInt())
    }
}

/** e.g. "Synced 5 minutes ago" or "Not synced". */
@Composable
fun SyncStatus.asString(): String =
    when (this) {
        SyncStatus.NotSynced -> stringResource(Res.string.sync_status_not_synced)
        is SyncStatus.Synced -> when (ago) {
            TimeAgo.JustNow -> stringResource(Res.string.sync_status_synced_just_now)
            is TimeAgo.Minutes -> pluralStringResource(Res.plurals.sync_status_synced_minutes_ago, ago.count, ago.count)
            is TimeAgo.Hours -> pluralStringResource(Res.plurals.sync_status_synced_hours_ago, ago.count, ago.count)
            is TimeAgo.Days -> pluralStringResource(Res.plurals.sync_status_synced_days_ago, ago.count, ago.count)
        }
    }
