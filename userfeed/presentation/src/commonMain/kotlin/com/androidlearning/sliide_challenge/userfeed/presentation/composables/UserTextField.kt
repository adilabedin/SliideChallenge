package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.domain.EmailValidationError
import com.androidlearning.sliide_challenge.userfeed.domain.NameValidationError
import com.androidlearning.sliide_challenge.userfeed.domain.UserValidator

/**
 * A single-line field for a user's name or email: shows [errorMessage] under the field, or a tick once [isValid].
 * [onFocusLost] fires when the user leaves the field, so the caller can decide when an error is worth showing.
 */
@Composable
fun UserTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    label: String,
    leadingIcon: ImageVector,
    errorMessage: String?,
    isValid: Boolean,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
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
        modifier = modifier
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

fun NameValidationError.toMessage(): String =
    when (this) {
        NameValidationError.EMPTY -> "Enter a name"
        NameValidationError.TOO_SHORT -> "Name must be at least ${UserValidator.NAME_MIN_LENGTH} characters"
        NameValidationError.TOO_LONG -> "Name must be at most ${UserValidator.NAME_MAX_LENGTH} characters"
        NameValidationError.INVALID_CHARACTERS -> "Use letters, spaces, hyphens, apostrophes or full stops"
    }

fun EmailValidationError.toMessage(): String =
    when (this) {
        EmailValidationError.EMPTY -> "Enter an email address"
        EmailValidationError.TOO_LONG -> "Email must be at most ${UserValidator.EMAIL_MAX_LENGTH} characters"
        EmailValidationError.INVALID_FORMAT -> "Enter a valid email, e.g. name@example.com"
    }

@Preview
@Composable
private fun UserTextFieldErrorPreview() {
    SliideTheme {
        UserTextField(
            value = "ada@example",
            onValueChange = {},
            onFocusLost = {},
            label = "Email",
            leadingIcon = Icons.Default.Email,
            errorMessage = EmailValidationError.INVALID_FORMAT.toMessage(),
            isValid = false,
            enabled = true,
            keyboardOptions = KeyboardOptions.Default,
        )
    }
}
