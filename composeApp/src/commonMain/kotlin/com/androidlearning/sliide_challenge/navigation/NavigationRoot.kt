package com.androidlearning.sliide_challenge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.androidlearning.sliide_challenge.core.presentation.splash.StartupState
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedRoot
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormMode
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormResult
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormRoot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data object UserFeedRoute

/**
 * Creates a user when both ids are null, otherwise edits the user with the given id. Routes only carry primitives, so
 * the two kinds of [FeedUserId] get one argument each.
 */
@Serializable
data class UserFormRoute(val createdLocalId: Long? = null, val remoteId: Long? = null) {
    fun toMode(): UserFormMode = when {
        createdLocalId != null -> UserFormMode.Edit(FeedUserId.Created(createdLocalId))
        remoteId != null -> UserFormMode.Edit(FeedUserId.Remote(remoteId))
        else -> UserFormMode.Create
    }

    companion object {
        fun edit(id: FeedUserId): UserFormRoute = when (id) {
            is FeedUserId.Created -> UserFormRoute(createdLocalId = id.localId)
            is FeedUserId.Remote -> UserFormRoute(remoteId = id.id)
        }
    }
}

/** Where the form leaves its [UserFormResult] on the feed's entry. Kept by name, so it survives process death. */
private const val KEY_FORM_RESULT = "form_result"

/**
 * @param startupState How the start is going. Passed as a flow and read inside the destination, so its changes don't
 * rebuild the navigation graph.
 */
@Composable
fun NavigationRoot(startupState: StateFlow<StartupState>) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = UserFeedRoute
    ) {
        composable<UserFeedRoute> { backStackEntry ->
            val startup by startupState.collectAsStateWithLifecycle()
            val savedStateHandle = backStackEntry.savedStateHandle
            val formResult by savedStateHandle.getStateFlow<String?>(KEY_FORM_RESULT, null).collectAsState()
            UserFeedRoot(
                startupState = startup,
                onAddUserClick = { navController.navigate(UserFormRoute()) },
                onEditUser = { id -> navController.navigate(UserFormRoute.edit(id)) },
                formResult = formResult?.let(UserFormResult::valueOf),
                onFormResultShown = { savedStateHandle[KEY_FORM_RESULT] = null }
            )
        }
        composable<UserFormRoute> { backStackEntry ->
            UserFormRoot(
                mode = backStackEntry.toRoute<UserFormRoute>().toMode(),
                onNavigateBack = { navController.navigateUp() },
                onFinished = { result ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_FORM_RESULT, result.name)
                    navController.navigateUp()
                }
            )
        }
    }
}
