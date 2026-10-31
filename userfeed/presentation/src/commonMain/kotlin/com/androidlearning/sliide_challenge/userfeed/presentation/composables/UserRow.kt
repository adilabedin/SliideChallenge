package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.FeedMode
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.FeedUserUi
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.SyncStatus
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.TimeAgo
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.asString
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.action_delete
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.action_edit
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.row_action_deselect
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.row_action_open
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.row_action_select
import org.jetbrains.compose.resources.stringResource

@Composable
fun UserRow(
    user: FeedUserUi,
    mode: FeedMode,
    isSelected: Boolean,
    isOpened: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val containerColor by animateColorAsState(
        if (isSelected || isOpened) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    )
    ListItem(
        modifier = Modifier.combinedClickable(
            onClickLabel = when (mode) {
                FeedMode.Browse -> stringResource(Res.string.row_action_open)

                FeedMode.Edit -> stringResource(Res.string.action_edit)

                FeedMode.Delete -> stringResource(
                    if (isSelected) Res.string.row_action_deselect else Res.string.row_action_select
                )
            },
            onClick = onClick,
            onLongClickLabel = stringResource(Res.string.action_delete),
            onLongClick = if (mode == FeedMode.Browse) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            } else {
                null
            }
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
                    text = user.syncStatus.asString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (user.isSynced) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
            }
        },
        trailingContent = if (mode == FeedMode.Edit) {
            { Icon(Icons.Default.Edit, contentDescription = null) }
        } else {
            null
        }
    )
}

@Preview
@Composable
private fun UserRowPreview() {
    SliideTheme {
        UserRow(
            user = FeedUserUi(
                FeedUserId.Remote(1),
                "Ada Lovelace",
                "ada@example.com",
                SyncStatus.Synced(TimeAgo.Minutes(5)),
                true
            ),
            mode = FeedMode.Delete,
            isSelected = true,
            isOpened = false,
            onClick = {},
            onLongClick = {}
        )
    }
}
