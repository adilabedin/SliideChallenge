package com.androidlearning.sliide_challenge

import androidx.compose.ui.window.ComposeUIViewController
import com.androidlearning.sliide_challenge.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = { initKoin() }
) {
    App()
}
