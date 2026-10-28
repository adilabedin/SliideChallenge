package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** "Not synced" while the user has a change waiting to sync ([lastSyncedAt] is null), else e.g. "Synced 5 minutes ago". */
fun formatSyncStatus(lastSyncedAt: Instant?, now: Instant): String =
    if (lastSyncedAt == null) "Not synced" else "Synced ${formatTimeAgo(lastSyncedAt, now)}"

/** Largest whole unit, rounded down. A time after [now] (the device clock moved back) reads as "just now". */
fun formatTimeAgo(then: Instant, now: Instant): String {
    val elapsed = now - then
    return when {
        elapsed < 1.minutes -> "just now"
        elapsed < 1.hours -> ago(elapsed.inWholeMinutes, "minute")
        elapsed < 1.days -> ago(elapsed.inWholeHours, "hour")
        else -> ago(elapsed.inWholeDays, "day")
    }
}

private fun ago(count: Long, unit: String): String = if (count == 1L) "1 $unit ago" else "$count ${unit}s ago"
