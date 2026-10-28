package com.androidlearning.sliide_challenge

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.navigation.NavigationRoot
import com.androidlearning.sliide_challenge.splash.MainViewModel
import com.androidlearning.sliide_challenge.splash.SplashContent
import org.koin.compose.viewmodel.koinViewModel

/**
 * @param mainViewModel Android passes the Activity's instance so the system splash and this
 * composable observe the same loading state.
 */
@Composable
fun App(
    mainViewModel: MainViewModel = koinViewModel(),
) {
    val state by mainViewModel.state.collectAsStateWithLifecycle()

    SliideTheme {
        Crossfade(targetState = state.isReady) { isReady ->
            if (isReady) {
                NavigationRoot()
            } else {
                SplashContent(
                    state = state,
                    onAction = mainViewModel::onAction,
                )
            }
        }
    }
}
