package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme

/** The name and email fields used wherever a user is created or edited; the keyboard's Done key calls [onDone]. */
@Composable
fun UserFields(
    name: String,
    email: String,
    nameErrorMessage: String?,
    emailErrorMessage: String?,
    isNameValid: Boolean,
    isEmailValid: Boolean,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onNameFocusLost: () -> Unit,
    onEmailFocusLost: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserTextField(
            value = name,
            onValueChange = onNameChange,
            onFocusLost = onNameFocusLost,
            label = "Name",
            leadingIcon = Icons.Default.Person,
            errorMessage = nameErrorMessage,
            isValid = isNameValid,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        UserTextField(
            value = email,
            onValueChange = onEmailChange,
            onFocusLost = onEmailFocusLost,
            label = "Email",
            leadingIcon = Icons.Default.Email,
            errorMessage = emailErrorMessage,
            isValid = isEmailValid,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
}

@Preview
@Composable
private fun UserFieldsPreview() {
    SliideTheme {
        UserFields(
            name = "Ada Lovelace",
            email = "ada@example",
            nameErrorMessage = null,
            emailErrorMessage = "Enter a valid email, e.g. name@example.com",
            isNameValid = true,
            isEmailValid = false,
            enabled = true,
            onNameChange = {},
            onEmailChange = {},
            onNameFocusLost = {},
            onEmailFocusLost = {},
            onDone = {},
        )
    }
}
