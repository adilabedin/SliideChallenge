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

    private fun ago(elapsed: Duration) = formatTimeAgo(now - elapsed, now)

    @Test
    fun `unsynced user reads not synced`() {
        assertThat(formatSyncStatus(lastSyncedAt = null, now = now)).isEqualTo("Not synced")
    }

    @Test
    fun `synced user reads synced with the time ago`() {
        assertThat(formatSyncStatus(lastSyncedAt = now - 5.minutes, now = now)).isEqualTo("Synced 5 minutes ago")
    }

    @Test
    fun `under a minute is just now`() {
        assertThat(ago(Duration.ZERO)).isEqualTo("just now")
        assertThat(ago(59.seconds)).isEqualTo("just now")
    }

    @Test
    fun `a time in the future is just now`() {
        assertThat(ago((-5).minutes)).isEqualTo("just now")
    }

    @Test
    fun `minutes are rounded down and singular for one`() {
        assertThat(ago(1.minutes)).isEqualTo("1 minute ago")
        assertThat(ago(5.minutes + 59.seconds)).isEqualTo("5 minutes ago")
        assertThat(ago(59.minutes)).isEqualTo("59 minutes ago")
    }

    @Test
    fun `hours start at sixty minutes`() {
        assertThat(ago(1.hours)).isEqualTo("1 hour ago")
        assertThat(ago(23.hours + 59.minutes)).isEqualTo("23 hours ago")
    }

    @Test
    fun `days start at twenty four hours`() {
        assertThat(ago(1.days)).isEqualTo("1 day ago")
        assertThat(ago(40.days)).isEqualTo("40 days ago")
    }
}
