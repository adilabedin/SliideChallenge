package com.androidlearning.sliide_challenge.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.components.AppLogo
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.core.designsystem.theme.SplashBackground
import com.androidlearning.sliide_challenge.core.domain.util.DataError

/**
 * Pixel-matches the native launch screens (brand colour, 160pt centred logo) so the hand-off is
 * invisible. iOS can't hold its launch screen, so this is what users see while loading there.
 */
@Composable
fun SplashContent(
    state: MainState,
    onAction: (MainAction) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground),
    ) {
        AppLogo(
            modifier = Modifier
                .align(Alignment.Center)
                .size(160.dp),
        )
        state.error?.let { error ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error.toMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onAction(MainAction.OnRetryClick) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = SplashBackground,
                    ),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

private fun DataError.Network.toMessage(): String = when (this) {
    DataError.Network.NO_INTERNET -> "No internet connection. Check your network and try again."
    DataError.Network.REQUEST_TIMEOUT -> "The request timed out. Please try again."
    DataError.Network.UNAUTHORIZED, DataError.Network.FORBIDDEN -> "Access denied. Check your API token."
    DataError.Network.TOO_MANY_REQUESTS -> "Too many requests. Please wait a moment."
    DataError.Network.SERVER_ERROR, DataError.Network.SERVICE_UNAVAILABLE -> "The server is unavailable right now."
    else -> "Something went wrong while loading users."
}

@Preview
@Composable
private fun SplashContentLoadingPreview() {
    SliideTheme {
        SplashContent(state = MainState(), onAction = {})
    }
}

@Preview
@Composable
private fun SplashContentErrorPreview() {
    SliideTheme {
        SplashContent(
            state = MainState(isLoading = false, error = DataError.Network.NO_INTERNET),
            onAction = {},
        )
    }
}
