package com.androidlearning.sliide_challenge.userfeed.presentation.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.core.presentation.util.ObserveAsEvents
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.DeleteUserButton
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.DeleteUserSheet
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.UserAvatar
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.UserDetails
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.UserFields
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.UserTopBar
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.toMessage
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.FeedUserUi
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [onDeleteConfirmed] must hand the id to the feed and close these details: the feed holds the delete back so it can
 * offer Undo (see [UserDetailEvent.DeleteConfirmed]). [showBackButton] is false when the details sit beside the list,
 * where there is nothing to go back to.
 */
@Composable
fun UserDetailRoot(
    userId: FeedUserId,
    showBackButton: Boolean,
    onClose: () -> Unit,
    onDeleteConfirmed: (FeedUserId) -> Unit,
    viewModel: UserDetailViewModel = koinViewModel { parametersOf(userId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UserDetailEvent.DeleteConfirmed -> onDeleteConfirmed(event.id)
            UserDetailEvent.UserNotFound -> onClose()
        }
    }

    UserDetailScreen(
        state = state,
        onAction = viewModel::onAction,
        onBackClick = onClose.takeIf { showBackButton },
    )
}

@Composable
fun UserDetailScreen(
    state: UserDetailState,
    onAction: (UserDetailAction) -> Unit,
    onBackClick: (() -> Unit)?,
) {
    // System back drops the unsaved edit before it leaves the screen.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.isEditing,
        onBackCompleted = { onAction(UserDetailAction.OnCancelEditClick) },
    )

    Scaffold(
        topBar = {
            UserTopBar(
                isEditing = state.isEditing,
                isSaving = state.isSaving,
                canEdit = state.user != null,
                canSave = state.canSave,
                onBackClick = onBackClick,
                onEditClick = { onAction(UserDetailAction.OnEditClick) },
                onSaveClick = { onAction(UserDetailAction.OnSaveClick) },
                onCancelEditClick = { onAction(UserDetailAction.OnCancelEditClick) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    // Phones use the full width; on tablets and in landscape the details stay a readable width.
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserAvatar()
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedContent(
                    targetState = state.isEditing,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { isEditing ->
                    if (isEditing) {
                        UserFields(
                            name = state.name,
                            email = state.email,
                            nameErrorMessage = state.nameErrorShown?.toMessage(),
                            emailErrorMessage = state.emailErrorShown?.toMessage(),
                            isNameValid = state.isNameValid,
                            isEmailValid = state.isEmailValid,
                            enabled = !state.isSaving,
                            onNameChange = { onAction(UserDetailAction.OnNameChange(it)) },
                            onEmailChange = { onAction(UserDetailAction.OnEmailChange(it)) },
                            onNameFocusLost = { onAction(UserDetailAction.OnNameFocusLost) },
                            onEmailFocusLost = { onAction(UserDetailAction.OnEmailFocusLost) },
                            onDone = { onAction(UserDetailAction.OnSaveClick) },
                        )
                    } else {
                        state.user?.let { user ->
                            UserDetails(
                                name = user.name,
                                email = user.email,
                                syncStatus = user.syncStatus,
                                isSynced = user.isSynced,
                            )
                        }
                    }
                }
            }
            DeleteUserButton(
                onClick = { onAction(UserDetailAction.OnDeleteClick) },
                enabled = state.user != null && !state.isSaving,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }

    val user = state.user
    if (state.isDeleteSheetVisible && user != null) {
        DeleteUserSheet(
            name = user.name,
            email = user.email,
            onConfirm = { onAction(UserDetailAction.OnDeleteConfirm) },
            onDismiss = { onAction(UserDetailAction.OnDeleteDismiss) },
        )
    }
}

private val previewUser = FeedUserUi(FeedUserId.Remote(1), "Ada Lovelace", "ada@example.com", "Synced 5 minutes ago", true)

@Preview
@Composable
private fun UserDetailScreenPreview() {
    SliideTheme {
        UserDetailScreen(
            state = UserDetailState(isLoading = false, user = previewUser),
            onAction = {},
            onBackClick = {},
        )
    }
}

@Preview
@Composable
private fun UserDetailScreenEditingPreview() {
    SliideTheme {
        UserDetailScreen(
            state = UserDetailState(
                isLoading = false,
                user = previewUser,
                isEditing = true,
                name = "Ada King",
                email = "ada@example.com",
            ),
            onAction = {},
            onBackClick = {},
        )
    }
}
