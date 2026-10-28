package com.androidlearning.sliide_challenge.userfeed.presentation.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.core.presentation.util.ObserveAsEvents
import com.androidlearning.sliide_challenge.userfeed.domain.EmailValidationError
import com.androidlearning.sliide_challenge.userfeed.domain.NameValidationError
import com.androidlearning.sliide_challenge.userfeed.domain.UserValidator
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
                InitialsAvatar(name = state.name)
                Spacer(modifier = Modifier.height(8.dp))
                FormTextField(
                    value = state.name,
                    onValueChange = { onAction(UserFormAction.OnNameChange(it)) },
                    onFocusLost = { onAction(UserFormAction.OnNameFocusLost) },
                    label = "Name",
                    leadingIcon = Icons.Default.Person,
                    errorMessage = state.nameErrorShown?.toMessage(),
                    isValid = state.isNameValid,
                    enabled = !state.isLoading && !state.isSaving,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
                FormTextField(
                    value = state.email,
                    onValueChange = { onAction(UserFormAction.OnEmailChange(it)) },
                    onFocusLost = { onAction(UserFormAction.OnEmailFocusLost) },
                    label = "Email",
                    leadingIcon = Icons.Default.Email,
                    errorMessage = if (state.isEmailTaken) {
                        "This email is already taken"
                    } else {
                        state.emailErrorShown?.toMessage()
                    },
                    isValid = state.isEmailValid,
                    enabled = !state.isLoading && !state.isSaving,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onAction(UserFormAction.OnSubmitClick) }),
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
private fun InitialsAvatar(name: String) {
    val initials = UserValidator.normalizeName(name)
        .split(' ')
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(88.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Crossfade(targetState = initials) { text ->
                if (text.isEmpty()) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(44.dp))
                } else {
                    Text(text = text, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    label: String,
    leadingIcon: ImageVector,
    errorMessage: String?,
    isValid: Boolean,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    // onFocusChanged also fires with "unfocused" when the field is first laid out; only a real blur counts.
    var hasBeenFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(visible = isValid, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Valid",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        // Reserve the line so the form doesn't jump when an error appears.
        supportingText = { Text(errorMessage ?: "") },
        isError = errorMessage != null,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    hasBeenFocused = true
                } else if (hasBeenFocused) {
                    onFocusLost()
                }
            },
    )
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

private fun NameValidationError.toMessage(): String =
    when (this) {
        NameValidationError.EMPTY -> "Enter a name"
        NameValidationError.TOO_SHORT -> "Name must be at least ${UserValidator.NAME_MIN_LENGTH} characters"
        NameValidationError.TOO_LONG -> "Name must be at most ${UserValidator.NAME_MAX_LENGTH} characters"
        NameValidationError.INVALID_CHARACTERS -> "Use letters, spaces, hyphens, apostrophes or full stops"
    }

private fun EmailValidationError.toMessage(): String =
    when (this) {
        EmailValidationError.EMPTY -> "Enter an email address"
        EmailValidationError.TOO_LONG -> "Email must be at most ${UserValidator.EMAIL_MAX_LENGTH} characters"
        EmailValidationError.INVALID_FORMAT -> "Enter a valid email, e.g. name@example.com"
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
