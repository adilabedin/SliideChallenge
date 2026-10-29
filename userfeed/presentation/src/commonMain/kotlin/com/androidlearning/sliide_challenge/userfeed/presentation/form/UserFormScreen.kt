package com.androidlearning.sliide_challenge.userfeed.presentation.form

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.core.presentation.util.ObserveAsEvents
import com.androidlearning.sliide_challenge.userfeed.domain.EmailValidationError
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.UserAvatar
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.UserFields
import com.androidlearning.sliide_challenge.userfeed.presentation.composables.toMessage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun UserFormRoot(
    mode: UserFormMode,
    onNavigateBack: () -> Unit,
    viewModel: UserFormViewModel = koinViewModel { parametersOf(mode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            UserFormEvent.Saved, UserFormEvent.UserNotFound -> onNavigateBack()
        }
    }

    UserFormScreen(
        state = state,
        onAction = viewModel::onAction,
        onCloseClick = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFormScreen(
    state: UserFormState,
    onAction: (UserFormAction) -> Unit,
    onCloseClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit user" else "New user") },
                navigationIcon = {
                    // Closing mid-save would hide a "email already taken" reply, so wait for the save to finish.
                    IconButton(onClick = onCloseClick, enabled = !state.isSaving) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Phones use the full width; on tablets and in landscape the form stays a readable width.
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserAvatar(name = state.name)
                Spacer(modifier = Modifier.height(8.dp))
                UserFields(
                    name = state.name,
                    email = state.email,
                    nameErrorMessage = state.nameErrorShown?.toMessage(),
                    emailErrorMessage = if (state.isEmailTaken) {
                        "This email is already taken"
                    } else {
                        state.emailErrorShown?.toMessage()
                    },
                    isNameValid = state.isNameValid,
                    isEmailValid = state.isEmailValid,
                    enabled = !state.isLoading && !state.isSaving,
                    onNameChange = { onAction(UserFormAction.OnNameChange(it)) },
                    onEmailChange = { onAction(UserFormAction.OnEmailChange(it)) },
                    onNameFocusLost = { onAction(UserFormAction.OnNameFocusLost) },
                    onEmailFocusLost = { onAction(UserFormAction.OnEmailFocusLost) },
                    onDone = { onAction(UserFormAction.OnSubmitClick) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SubmitButton(
                    text = if (state.isEditing) "Save changes" else "Create user",
                    isSaving = state.isSaving,
                    enabled = state.canSubmit,
                    onClick = { onAction(UserFormAction.OnSubmitClick) },
                )
            }
        }
    }
}

@Composable
private fun SubmitButton(
    text: String,
    isSaving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Crossfade(targetState = isSaving) { saving ->
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(text)
            }
        }
    }
}

@Preview
@Composable
private fun UserFormScreenCreatePreview() {
    SliideTheme {
        UserFormScreen(state = UserFormState(), onAction = {}, onCloseClick = {})
    }
}

@Preview
@Composable
private fun UserFormScreenErrorsPreview() {
    SliideTheme {
        UserFormScreen(
            state = UserFormState(
                name = "Ada Lovelace",
                email = "ada@example",
                nameError = null,
                emailError = EmailValidationError.INVALID_FORMAT,
                isNameTouched = true,
                isEmailTouched = true,
            ),
            onAction = {},
            onCloseClick = {},
        )
    }
}

@Preview
@Composable
private fun UserFormScreenEditSavingPreview() {
    SliideTheme {
        UserFormScreen(
            state = UserFormState(
                isEditing = true,
                isSaving = true,
                name = "Grace Hopper",
                email = "grace@example.com",
                nameError = null,
                emailError = null,
            ),
            onAction = {},
            onCloseClick = {},
        )
    }
}
