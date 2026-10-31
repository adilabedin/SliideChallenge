package com.androidlearning.sliide_challenge

import androidx.compose.runtime.Composable
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.navigation.NavigationRoot

@Composable
fun App() {
    SliideTheme {
        NavigationRoot()
    }
}
