package com.androidlearning.sliide_challenge

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

/**
 * Koin is already started by [startIosApp]. Screens make room for the keyboard themselves with `imePadding()`, so
 * the controller must not also shift the whole view up when a field gains focus: the two together take focus away
 * again as the keyboard opens, and a field needs a second tap.
 */
fun MainViewController() = ComposeUIViewController(
    configure = { onFocusBehavior = OnFocusBehavior.DoNothing }
) {
    App()
}
