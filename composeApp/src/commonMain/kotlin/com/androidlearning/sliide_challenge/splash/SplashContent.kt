package com.androidlearning.sliide_challenge.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.components.AppLogo
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.core.designsystem.theme.SplashBackground

/**
 * Pixel-matches the native launch screens (brand colour, 160pt centred logo) so the hand-off is
 * invisible. iOS can't hold its launch screen, so this is what users see while loading there.
 */
@Composable
fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground),
        contentAlignment = Alignment.Center,
    ) {
        AppLogo(modifier = Modifier.size(160.dp))
    }
}

@Preview
@Composable
private fun SplashContentPreview() {
    SliideTheme {
        SplashContent()
    }
}
