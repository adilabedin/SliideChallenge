package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SyncStatusFormatterTest {

    private val now = Instant.fromEpochMilliseconds(1_000_000_000)

    private fun ago(elapsed: Duration) = timeAgo(now - elapsed, now)

    @Test
    fun `unsynced user is not synced`() {
        assertThat(syncStatusOf(lastSyncedAt = null, now = now)).isEqualTo(SyncStatus.NotSynced)
    }

    @Test
    fun `synced user is synced with the time ago`() {
        assertThat(syncStatusOf(lastSyncedAt = now - 5.minutes, now = now))
            .isEqualTo(SyncStatus.Synced(TimeAgo.Minutes(5)))
    }

    @Test
    fun `under a minute is just now`() {
        assertThat(ago(Duration.ZERO)).isEqualTo(TimeAgo.JustNow)
        assertThat(ago(59.seconds)).isEqualTo(TimeAgo.JustNow)
    }

    @Test
    fun `a time in the future is just now`() {
        assertThat(ago((-5).minutes)).isEqualTo(TimeAgo.JustNow)
    }

    @Test
    fun `minutes are rounded down`() {
        assertThat(ago(1.minutes)).isEqualTo(TimeAgo.Minutes(1))
        assertThat(ago(5.minutes + 59.seconds)).isEqualTo(TimeAgo.Minutes(5))
        assertThat(ago(59.minutes)).isEqualTo(TimeAgo.Minutes(59))
    }

    @Test
    fun `hours start at sixty minutes`() {
        assertThat(ago(1.hours)).isEqualTo(TimeAgo.Hours(1))
        assertThat(ago(23.hours + 59.minutes)).isEqualTo(TimeAgo.Hours(23))
    }

    @Test
    fun `days start at twenty four hours`() {
        assertThat(ago(1.days)).isEqualTo(TimeAgo.Days(1))
        assertThat(ago(40.days)).isEqualTo(TimeAgo.Days(40))
    }
}
