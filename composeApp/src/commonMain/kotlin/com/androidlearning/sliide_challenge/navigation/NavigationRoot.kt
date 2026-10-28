package com.androidlearning.sliide_challenge.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedRoot
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormMode
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormRoot
import kotlinx.serialization.Serializable

@Serializable
data object UserFeedRoute

/**
 * Creates a user when both ids are null, otherwise edits the user with the given id. Routes only carry primitives, so
 * the two kinds of [FeedUserId] get one argument each.
 */
@Serializable
data class UserFormRoute(
    val createdLocalId: Long? = null,
    val remoteId: Long? = null,
) {
    fun toMode(): UserFormMode =
        when {
            createdLocalId != null -> UserFormMode.Edit(FeedUserId.Created(createdLocalId))
            remoteId != null -> UserFormMode.Edit(FeedUserId.Remote(remoteId))
            else -> UserFormMode.Create
        }
}

@Composable
fun NavigationRoot() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = UserFeedRoute,
    ) {
        composable<UserFeedRoute> {
            UserFeedRoot(
                onAddUserClick = { navController.navigate(UserFormRoute()) },
            )
        }
        composable<UserFormRoute> { backStackEntry ->
            UserFormRoot(
                mode = backStackEntry.toRoute<UserFormRoute>().toMode(),
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
