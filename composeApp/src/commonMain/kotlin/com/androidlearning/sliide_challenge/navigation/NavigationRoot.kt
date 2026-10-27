package com.androidlearning.sliide_challenge.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedRoot
import kotlinx.serialization.Serializable

@Serializable
data object UserFeedRoute

@Composable
fun NavigationRoot() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = UserFeedRoute,
    ) {
        composable<UserFeedRoute> {
            UserFeedRoot()
        }
    }
}
