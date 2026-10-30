package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.androidlearning.sliide_challenge.core.designsystem.components.layouts.SliideAdaptiveListDetailLayout
import com.androidlearning.sliide_challenge.core.designsystem.components.layouts.showsListDetailOnePaneAtATime
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.core.presentation.splash.StartupState
import com.androidlearning.sliide_challenge.core.presentation.util.ObserveAsEvents
import com.androidlearning.sliide_challenge.core.presentation.util.currentDeviceConfiguration
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.DeleteUserSheet
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.DeleteUsersSheet
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.EmptyUsersContent
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.NoUserOpenedContent
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.UserFeedTopBar
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.UserList
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.UserListPlaceholder
import com.androidlearning.sliide_challenge.userfeed.presentation.detail.UserDetailRoot
import com.androidlearning.sliide_challenge.userfeed.presentation.form.UserFormResult
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_add_user
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The feed and the opened user's details. A phone held upright shows one at a time; wider windows show both side by
 * side (see [SliideAdaptiveListDetailLayout]). Both arrangements are one destination, so turning the device mid-edit
 * keeps the edit: each user's details ViewModel lives in this destination, keyed by the user.
 *
 * @param startupState How the app's start is going; the feed shimmers while it loads and reports a failure once.
 * @param formResult Why the user form last closed itself, to announce here since the form is gone. Cleared through
 * [onFormResultShown] once it is on its way, so it shows once.
 */
@Composable
fun UserFeedRoot(
    startupState: StartupState,
    onAddUserClick: () -> Unit,
    onEditUser: (FeedUserId) -> Unit,
    formResult: UserFormResult? = null,
    onFormResultShown: () -> Unit = {},
    viewModel: UserFeedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val configuration = currentDeviceConfiguration()
    val isOnePaneAtATime = configuration.showsListDetailOnePaneAtATime
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(startupState) {
        viewModel.onAction(UserFeedAction.OnStartupStateChange(startupState))
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UserFeedEvent.EditUser -> onEditUser(event.id)

            is UserFeedEvent.RefreshFailed -> scope.launch {
                snackbarHostState.showSnackbar(getString(event.error.toRefreshMessage()))
            }
        }
    }

    // Shown on the screen's scope rather than this effect's, so clearing the result doesn't take the message down.
    LaunchedEffect(formResult) {
        if (formResult == null) return@LaunchedEffect
        scope.launch { snackbarHostState.showSnackbar(getString(formResult.message)) }
        onFormResultShown()
    }

    UndoDeleteSnackbarEffect(
        deletedUsers = state.deletedUsers,
        snackbarHostState = snackbarHostState,
        onUndoClick = { viewModel.onAction(UserFeedAction.OnUndoDeleteClick) }
    )

    // Beside the list the ViewModel shows the first user until another is picked; over it, only a tap opens one.
    LaunchedEffect(isOnePaneAtATime) {
        viewModel.onAction(UserFeedAction.OnLayoutChange(showsDetailsBesideList = !isOnePaneAtATime))
    }

    // On a phone the details cover the list, so system back returns to it.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isOnePaneAtATime && state.openedUserId != null,
        onBackCompleted = { viewModel.onAction(UserFeedAction.OnCloseUserClick) }
    )

    SliideAdaptiveListDetailLayout(
        selectedItem = state.openedUserId,
        configuration = configuration,
        list = {
            UserFeedScreen(
                state = state,
                onAction = viewModel::onAction,
                onAddUserClick = onAddUserClick,
                snackbarHostState = snackbarHostState
            )
        },
        detail = { userId ->
            UserDetailRoot(
                userId = userId,
                showBackButton = isOnePaneAtATime,
                onClose = { viewModel.onAction(UserFeedAction.OnCloseUserClick) },
                // The feed holds the delete back for Undo; the details close with it, so the snackbar is in view.
                onDeleteConfirmed = { viewModel.onAction(UserFeedAction.OnDeleteRequested(it)) },
                viewModel = koinViewModel(key = "user-detail-${userId.listKey()}") { parametersOf(userId) }
            )
        },
        emptyDetail = { NoUserOpenedContent() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFeedScreen(
    state: UserFeedState,
    onAction: (UserFeedAction) -> Unit,
    onAddUserClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val listState = rememberLazyListState()
    // Shrink to an icon once the list scrolls, so the button covers less of it.
    val isFabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    // System back leaves Edit or Delete mode before it leaves the screen.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.mode != FeedMode.Browse,
        onBackCompleted = { onAction(UserFeedAction.OnExitModeClick) }
    )

    Scaffold(
        topBar = {
            UserFeedTopBar(
                mode = state.mode,
                selectedCount = state.selectedIds.size,
                canChangeMode = state.users.isNotEmpty() && !state.isRefreshing,
                onAction = onAction
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Adding a user mid-selection would be confusing, so the button steps aside while a mode is active.
            AnimatedVisibility(
                visible = state.mode == FeedMode.Browse,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(Res.string.feed_add_user)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onAddUserClick,
                    expanded = isFabExpanded
                )
            }
        }
    ) { innerPadding ->
        // The cached users stay on screen during a refresh, with the box's spinner above them; the new page then
        // animates in row by row. The box won't fire onRefresh again while it is refreshing.
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(UserFeedAction.OnRefresh) },
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // The content follows the finger, opening a gap for the indicator, holds it while refreshing and slides
            // back up afterwards. Read in the layer block, so the pull moves pixels without recomposing the list.
            val modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = pullState.distanceFraction * PullToRefreshDefaults.PositionalThreshold.toPx()
                }
            when {
                state.isLoading -> UserListPlaceholder(modifier)

                state.isEmpty -> EmptyUsersContent(
                    onRetryClick = { onAction(UserFeedAction.OnRefresh) },
                    loadError = state.loadError,
                    modifier = modifier
                )

                else -> UserList(
                    users = state.users,
                    mode = state.mode,
                    selectedIds = state.selectedIds,
                    openedUserId = state.openedUserId,
                    listState = listState,
                    onUserClick = { onAction(UserFeedAction.OnUserClick(it)) },
                    onUserLongClick = { onAction(UserFeedAction.OnUserLongClick(it)) },
                    modifier = modifier
                )
            }
        }
    }

    val usersToDelete = state.usersToDelete
    val userToDelete = usersToDelete.singleOrNull()
    when {
        userToDelete != null -> DeleteUserSheet(
            name = userToDelete.name,
            email = userToDelete.email,
            onConfirm = { onAction(UserFeedAction.OnDeleteConfirm) },
            onDismiss = { onAction(UserFeedAction.OnDeleteDismiss) }
        )

        usersToDelete.isNotEmpty() -> DeleteUsersSheet(
            count = usersToDelete.size,
            onConfirm = { onAction(UserFeedAction.OnDeleteConfirm) },
            onDismiss = { onAction(UserFeedAction.OnDeleteDismiss) }
        )
    }
}

@Preview
@Composable
private fun UserFeedScreenPreview() {
    SliideTheme {
        UserFeedScreen(
            state = UserFeedState(
                isLoading = false,
                users = listOf(
                    FeedUserUi(FeedUserId.Created(1), "Grace Hopper", "grace@example.com", SyncStatus.NotSynced, false),
                    FeedUserUi(
                        FeedUserId.Remote(2),
                        "Alan Turing",
                        "alan@example.com",
                        SyncStatus.Synced(TimeAgo.JustNow),
                        true
                    ),
                    FeedUserUi(
                        FeedUserId.Remote(1),
                        "Ada Lovelace",
                        "ada@example.com",
                        SyncStatus.Synced(TimeAgo.Minutes(5)),
                        true
                    )
                )
            ),
            onAction = {},
            onAddUserClick = {}
        )
    }
}

@Preview
@Composable
private fun UserFeedScreenDeleteModePreview() {
    SliideTheme {
        UserFeedScreen(
            state = UserFeedState(
                isLoading = false,
                users = listOf(
                    FeedUserUi(
                        FeedUserId.Remote(2),
                        "Alan Turing",
                        "alan@example.com",
                        SyncStatus.Synced(TimeAgo.JustNow),
                        true
                    ),
                    FeedUserUi(
                        FeedUserId.Remote(1),
                        "Ada Lovelace",
                        "ada@example.com",
                        SyncStatus.Synced(TimeAgo.Minutes(5)),
                        true
                    )
                ),
                mode = FeedMode.Delete,
                selectedIds = setOf(FeedUserId.Remote(1))
            ),
            onAction = {},
            onAddUserClick = {}
        )
    }
}

@Preview
@Composable
private fun UserFeedScreenRefreshingPreview() {
    SliideTheme {
        UserFeedScreen(
            state = UserFeedState(
                isLoading = false,
                isRefreshing = true,
                users = listOf(
                    FeedUserUi(
                        FeedUserId.Remote(1),
                        "Ada Lovelace",
                        "ada@example.com",
                        SyncStatus.Synced(TimeAgo.Minutes(5)),
                        true
                    )
                )
            ),
            onAction = {},
            onAddUserClick = {}
        )
    }
}

@Preview
@Composable
private fun UserFeedScreenEmptyPreview() {
    SliideTheme {
        UserFeedScreen(state = UserFeedState(isLoading = false), onAction = {}, onAddUserClick = {})
    }
}
