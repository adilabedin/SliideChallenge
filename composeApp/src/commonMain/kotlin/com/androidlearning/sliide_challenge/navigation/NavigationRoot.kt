package com.androidlearning.sliide_challenge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.detail.UserDetailRoot
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedAction
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedRoot
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedViewModel
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormMode
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormRoot
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

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

    companion object {
        fun edit(id: FeedUserId): UserFormRoute =
            when (id) {
                is FeedUserId.Created -> UserFormRoute(createdLocalId = id.localId)
                is FeedUserId.Remote -> UserFormRoute(remoteId = id.id)
            }
    }
}

/** Shows one user; like [UserFormRoute], exactly one of the two ids is set. */
@Serializable
data class UserDetailRoute(
    val createdLocalId: Long? = null,
    val remoteId: Long? = null,
) {
    fun toUserId(): FeedUserId =
        createdLocalId?.let { FeedUserId.Created(it) } ?: FeedUserId.Remote(requireNotNull(remoteId))

    companion object {
        fun of(id: FeedUserId): UserDetailRoute =
            when (id) {
                is FeedUserId.Created -> UserDetailRoute(createdLocalId = id.localId)
                is FeedUserId.Remote -> UserDetailRoute(remoteId = id.id)
            }
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
                onOpenUser = { id -> navController.navigate(UserDetailRoute.of(id)) },
                onEditUser = { id -> navController.navigate(UserFormRoute.edit(id)) },
            )
        }
        composable<UserDetailRoute> { backStackEntry ->
            // The feed owns the Undo window, so a delete confirmed here is handed to the feed's ViewModel, which is
            // still alive underneath, and the user lands back on the feed with Undo on offer.
            val feedEntry = remember(backStackEntry) { navController.getBackStackEntry<UserFeedRoute>() }
            val feedViewModel = koinViewModel<UserFeedViewModel>(viewModelStoreOwner = feedEntry)
            UserDetailRoot(
                userId = backStackEntry.toRoute<UserDetailRoute>().toUserId(),
                onNavigateBack = { navController.navigateUp() },
                onDeleteConfirmed = { id ->
                    feedViewModel.onAction(UserFeedAction.OnDeleteRequested(id))
                    navController.navigateUp()
                },
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
