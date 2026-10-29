package com.androidlearning.sliide_challenge.core.presentation.util

import androidx.window.core.layout.WindowSizeClass
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DeviceConfigurationTest {

    // Window size classes hold the lower bound of each bucket, so the real dp sizes are noted alongside.
    private fun configurationOf(widthDp: Int, heightDp: Int): DeviceConfiguration =
        DeviceConfiguration.fromWindowSizeClass(WindowSizeClass.compute(widthDp.toFloat(), heightDp.toFloat()))

    @Test
    fun `a phone held upright is mobile portrait`() {
        assertThat(configurationOf(widthDp = 411, heightDp = 891)).isEqualTo(DeviceConfiguration.MOBILE_PORTRAIT)
    }

    @Test
    fun `a narrow and short window is still mobile portrait`() {
        assertThat(configurationOf(widthDp = 400, heightDp = 300)).isEqualTo(DeviceConfiguration.MOBILE_PORTRAIT)
    }

    @Test
    fun `a large phone on its side is mobile landscape`() {
        assertThat(configurationOf(widthDp = 891, heightDp = 411)).isEqualTo(DeviceConfiguration.MOBILE_LANDSCAPE)
    }

    @Test
    fun `a small phone on its side is mobile landscape rather than desktop`() {
        assertThat(configurationOf(widthDp = 740, heightDp = 360)).isEqualTo(DeviceConfiguration.MOBILE_LANDSCAPE)
    }

    @Test
    fun `a tablet held upright is tablet portrait`() {
        assertThat(configurationOf(widthDp = 800, heightDp = 1280)).isEqualTo(DeviceConfiguration.TABLET_PORTRAIT)
    }

    @Test
    fun `an unfolded foldable is tablet portrait rather than desktop`() {
        assertThat(configurationOf(widthDp = 673, heightDp = 841)).isEqualTo(DeviceConfiguration.TABLET_PORTRAIT)
    }

    @Test
    fun `a tablet on its side is tablet landscape`() {
        assertThat(configurationOf(widthDp = 1280, heightDp = 800)).isEqualTo(DeviceConfiguration.TABLET_LANDSCAPE)
    }

    @Test
    fun `a large window is desktop`() {
        assertThat(configurationOf(widthDp = 1440, heightDp = 960)).isEqualTo(DeviceConfiguration.DESKTOP)
    }
}
