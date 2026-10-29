package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide_challenge.core.designsystem.components.layouts.SliideAdaptiveListDetailLayout
import com.androidlearning.sliide_challenge.core.designsystem.components.layouts.showsListDetailOnePaneAtATime
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.core.presentation.util.ObserveAsEvents
import com.androidlearning.sliide_challenge.core.presentation.util.currentDeviceConfiguration
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.DeleteUserSheet
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.DeleteUsersSheet
import com.androidlearning.sliide_challenge.userfeed.presentation.detail.UserDetailRoot
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The feed and the opened user's details. A phone held upright shows one at a time; wider windows show both side by
 * side (see [SliideAdaptiveListDetailLayout]). Both arrangements are one destination, so turning the device mid-edit
 * keeps the edit: each user's details ViewModel lives in this destination, keyed by the user.
 */
@Composable
fun UserFeedRoot(
    onAddUserClick: () -> Unit,
    onEditUser: (FeedUserId) -> Unit,
    viewModel: UserFeedViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val configuration = currentDeviceConfiguration()
    val isOnePaneAtATime = configuration.showsListDetailOnePaneAtATime

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UserFeedEvent.EditUser -> onEditUser(event.id)
        }
    }

    // Beside the list the ViewModel shows the first user until another is picked; over it, only a tap opens one.
    LaunchedEffect(isOnePaneAtATime) {
        viewModel.onAction(UserFeedAction.OnLayoutChange(showsDetailsBesideList = !isOnePaneAtATime))
    }

    // On a phone the details cover the list, so system back returns to it.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isOnePaneAtATime && state.openedUserId != null,
        onBackCompleted = { viewModel.onAction(UserFeedAction.OnCloseUserClick) },
    )

    SliideAdaptiveListDetailLayout(
        selectedItem = state.openedUserId,
        configuration = configuration,
        list = {
            UserFeedScreen(
                state = state,
                onAction = viewModel::onAction,
                onAddUserClick = onAddUserClick,
            )
        },
        detail = { userId ->
            UserDetailRoot(
                userId = userId,
                showBackButton = isOnePaneAtATime,
                onClose = { viewModel.onAction(UserFeedAction.OnCloseUserClick) },
                // The feed holds the delete back for Undo; the details close with it, so the snackbar is in view.
                onDeleteConfirmed = { viewModel.onAction(UserFeedAction.OnDeleteRequested(it)) },
                viewModel = koinViewModel(key = "user-detail-${userId.listKey()}") { parametersOf(userId) },
            )
        },
        emptyDetail = { NoUserOpenedContent() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFeedScreen(
    state: UserFeedState,
    onAction: (UserFeedAction) -> Unit,
    onAddUserClick: () -> Unit,
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    UndoDeleteSnackbar(
        deletedUsers = state.deletedUsers,
        snackbarHostState = snackbarHostState,
        onUndoClick = { onAction(UserFeedAction.OnUndoDeleteClick) },
    )
    // Shrink to an icon once the list scrolls, so the button covers less of it.
    val isFabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    // System back leaves Edit or Delete mode before it leaves the screen.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.mode != FeedMode.Browse,
        onBackCompleted = { onAction(UserFeedAction.OnExitModeClick) },
    )

    Scaffold(
        topBar = {
            UserFeedTopBar(
                mode = state.mode,
                selectedCount = state.selectedIds.size,
                canChangeMode = state.users.isNotEmpty(),
                onAction = onAction,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Adding a user mid-selection would be confusing, so the button steps aside while a mode is active.
            AnimatedVisibility(
                visible = state.mode == FeedMode.Browse,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text("Add user") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onAddUserClick,
                    expanded = isFabExpanded,
                )
            }
        },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when {
            state.isLoading -> Box(modifier)
            state.isEmpty -> EmptyUsersContent(modifier)
            else -> UserList(
                users = state.users,
                mode = state.mode,
                selectedIds = state.selectedIds,
                openedUserId = state.openedUserId,
                listState = listState,
                onUserClick = { onAction(UserFeedAction.OnUserClick(it)) },
                onUserLongClick = { onAction(UserFeedAction.OnUserLongClick(it)) },
                modifier = modifier,
            )
        }
    }

    val usersToDelete = state.usersToDelete
    val userToDelete = usersToDelete.singleOrNull()
    when {
        userToDelete != null -> DeleteUserSheet(
            name = userToDelete.name,
            email = userToDelete.email,
            onConfirm = { onAction(UserFeedAction.OnDeleteConfirm) },
            onDismiss = { onAction(UserFeedAction.OnDeleteDismiss) },
        )
        usersToDelete.isNotEmpty() -> DeleteUsersSheet(
            count = usersToDelete.size,
            onConfirm = { onAction(UserFeedAction.OnDeleteConfirm) },
            onDismiss = { onAction(UserFeedAction.OnDeleteDismiss) },
        )
    }
}

/**
 * Browsing shows the title and a meatball menu for picking Edit or Delete mode. Inside a mode the bar takes a tinted
 * container, so it is obvious taps now edit or select, and offers a close button (plus Delete for the selection).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserFeedTopBar(
    mode: FeedMode,
    selectedCount: Int,
    canChangeMode: Boolean,
    onAction: (UserFeedAction) -> Unit,
) {
    val containerColor by animateColorAsState(
        if (mode == FeedMode.Browse) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer,
    )
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
        title = {
            Text(
                when (mode) {
                    FeedMode.Browse -> "Users"
                    FeedMode.Edit -> "Tap a user to edit"
                    FeedMode.Delete -> if (selectedCount == 0) "Select users" else "$selectedCount selected"
                },
            )
        },
        navigationIcon = {
            if (mode != FeedMode.Browse) {
                IconButton(onClick = { onAction(UserFeedAction.OnExitModeClick) }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            }
        },
        actions = {
            when (mode) {
                FeedMode.Browse -> FeedOverflowMenu(enabled = canChangeMode, onAction = onAction)
                FeedMode.Edit -> Unit
                FeedMode.Delete -> IconButton(
                    onClick = { onAction(UserFeedAction.OnDeleteSelectedClick) },
                    enabled = selectedCount > 0,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                }
            }
        },
    )
}

@Composable
private fun FeedOverflowMenu(
    enabled: Boolean,
    onAction: (UserFeedAction) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }, enabled = enabled) {
            // The core icon set has no horizontal "more" icon, so the vertical one is turned on its side.
            Icon(Icons.Default.MoreVert, contentDescription = "More options", modifier = Modifier.rotate(90f))
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    isExpanded = false
                    onAction(UserFeedAction.OnEditModeClick)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    isExpanded = false
                    onAction(UserFeedAction.OnDeleteModeClick)
                },
            )
        }
    }
}

/**
 * Offers Undo for as long as the ViewModel keeps [deletedUsers]; once they clear (Undo expired or another delete took
 * its place) the effect restarts, which dismisses the snackbar. The ViewModel owns the timer, so the delete still
 * goes through if this screen leaves composition first.
 */
@Composable
private fun UndoDeleteSnackbar(
    deletedUsers: List<FeedUserUi>,
    snackbarHostState: SnackbarHostState,
    onUndoClick: () -> Unit,
) {
    LaunchedEffect(deletedUsers) {
        if (deletedUsers.isEmpty()) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedUsers.singleOrNull()?.let { "${it.name} deleted" } ?: "${deletedUsers.size} users deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoClick()
    }
}

@Composable
private fun UserList(
    users: List<FeedUserUi>,
    mode: FeedMode,
    selectedIds: Set<FeedUserId>,
    openedUserId: FeedUserId?,
    listState: LazyListState,
    onUserClick: (FeedUserUi) -> Unit,
    onUserLongClick: (FeedUserUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollToTopOnUserCreated(users = users, listState = listState)
    LazyColumn(
        state = listState,
        modifier = modifier,
        // Room for the FAB, so it never covers the last row.
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(items = users, key = { it.id.listKey() }) { user ->
            Column(modifier = Modifier.animateItem()) {
                UserRow(
                    user = user,
                    mode = mode,
                    isSelected = user.id in selectedIds,
                    // Beside the details on a wide screen, the row shows whose details they are.
                    isOpened = mode == FeedMode.Browse && user.id == openedUserId,
                    onClick = { onUserClick(user) },
                    onLongClick = { onUserLongClick(user) },
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * A created user is inserted at the top, but a keyed list keeps the previous first row in place, so the new row could
 * land just above the viewport. Local ids only ever grow, so a new user is one with a higher id than any seen before;
 * a count would also rise when an undone delete brings a row back. The highest id is saveable so this still fires on
 * returning from the form, when the feed has been out of composition while the user was created.
 */
@Composable
private fun ScrollToTopOnUserCreated(
    users: List<FeedUserUi>,
    listState: LazyListState,
) {
    val newestCreatedId = users.maxOfOrNull { (it.id as? FeedUserId.Created)?.localId ?: 0L } ?: 0L
    var highestSeenId by rememberSaveable { mutableLongStateOf(newestCreatedId) }
    LaunchedEffect(newestCreatedId) {
        if (newestCreatedId > highestSeenId) {
            listState.animateScrollToItem(0)
            highestSeenId = newestCreatedId
        }
    }
}

@Composable
private fun UserRow(
    user: FeedUserUi,
    mode: FeedMode,
    isSelected: Boolean,
    isOpened: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val containerColor by animateColorAsState(
        if (isSelected || isOpened) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    )
    ListItem(
        modifier = Modifier.combinedClickable(
            onClickLabel = when (mode) {
                FeedMode.Browse -> "Open"
                FeedMode.Edit -> "Edit"
                FeedMode.Delete -> if (isSelected) "Deselect" else "Select"
            },
            onClick = onClick,
            onLongClickLabel = "Delete",
            onLongClick = if (mode == FeedMode.Browse) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            } else {
                null
            },
        ),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = if (mode == FeedMode.Delete) {
            // The row handles the tap, so the checkbox only shows the state.
            { Checkbox(checked = isSelected, onCheckedChange = null) }
        } else {
            null
        },
        headlineContent = { Text(user.name) },
        supportingContent = {
            Column {
                Text(user.email)
                Text(
                    text = user.syncStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (user.isSynced) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
            }
        },
        trailingContent = if (mode == FeedMode.Edit) {
            { Icon(Icons.Default.Edit, contentDescription = null) }
        } else {
            null
        },
    )
}

/** Lazy-list keys must be saveable on Android, so the sealed id is flattened to a string unique across both kinds. */
internal fun FeedUserId.listKey(): String =
    when (this) {
        is FeedUserId.Created -> "created-$localId"
        is FeedUserId.Remote -> "remote-$id"
    }

@Composable
private fun EmptyUsersContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No users yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Users you add will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Stands in for the details beside the list while there are no users to show. */
@Composable
private fun NoUserOpenedContent() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No user to show",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add a user and their details will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
                    FeedUserUi(FeedUserId.Created(1), "Grace Hopper", "grace@example.com", "Not synced", false),
                    FeedUserUi(FeedUserId.Remote(2), "Alan Turing", "alan@example.com", "Synced just now", true),
                    FeedUserUi(FeedUserId.Remote(1), "Ada Lovelace", "ada@example.com", "Synced 5 minutes ago", true),
                ),
            ),
            onAction = {},
            onAddUserClick = {},
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
                    FeedUserUi(FeedUserId.Remote(2), "Alan Turing", "alan@example.com", "Synced just now", true),
                    FeedUserUi(FeedUserId.Remote(1), "Ada Lovelace", "ada@example.com", "Synced 5 minutes ago", true),
                ),
                mode = FeedMode.Delete,
                selectedIds = setOf(FeedUserId.Remote(1)),
            ),
            onAction = {},
            onAddUserClick = {},
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
