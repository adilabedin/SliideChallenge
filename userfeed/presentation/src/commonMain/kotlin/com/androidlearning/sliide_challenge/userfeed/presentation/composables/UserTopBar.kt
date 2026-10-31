package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.action_back
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.action_save
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.detail_cancel_editing
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.detail_edit_user
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.detail_title
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.detail_title_editing
import org.jetbrains.compose.resources.stringResource

/**
 * The app bar of a screen that shows a user and edits them in place. Viewing shows a back arrow and an edit button.
 * Editing tints the bar, like the feed's modes, swaps the back arrow for a close button that drops the edit, and the
 * edit button for Save. Without [onBackClick] there is no back arrow, e.g. beside the list on a wide screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserTopBar(
    isEditing: Boolean,
    isSaving: Boolean,
    canEdit: Boolean,
    canSave: Boolean,
    onBackClick: (() -> Unit)?,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        if (isEditing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    )
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
        title = { Text(stringResource(if (isEditing) Res.string.detail_title_editing else Res.string.detail_title)) },
        navigationIcon = {
            if (isEditing) {
                IconButton(onClick = onCancelEditClick, enabled = !isSaving) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.detail_cancel_editing))
                }
            } else if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back)
                    )
                }
            }
        },
        actions = {
            AnimatedContent(
                targetState = isEditing,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { editing ->
                if (editing) {
                    TextButton(onClick = onSaveClick, enabled = canSave) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(Res.string.action_save))
                        }
                    }
                } else {
                    IconButton(onClick = onEditClick, enabled = canEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.detail_edit_user))
                    }
                }
            }
        }
    )
}

@Preview
@Composable
private fun UserTopBarPreview() {
    SliideTheme {
        UserTopBar(
            isEditing = false,
            isSaving = false,
            canEdit = true,
            canSave = false,
            onBackClick = {},
            onEditClick = {},
            onSaveClick = {},
            onCancelEditClick = {}
        )
    }
}

@Preview
@Composable
private fun UserTopBarEditingPreview() {
    SliideTheme {
        UserTopBar(
            isEditing = true,
            isSaving = false,
            canEdit = true,
            canSave = true,
            onBackClick = {},
            onEditClick = {},
            onSaveClick = {},
            onCancelEditClick = {}
        )
    }
}
