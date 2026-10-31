package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.FeedMode
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.UserFeedAction
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.action_cancel
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.action_delete
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.action_edit
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_delete_selected
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_more_options
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_title
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_title_edit_mode
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_title_select_users
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_title_selected_count
import org.jetbrains.compose.resources.stringResource

/**
 * Browsing shows the title and a meatball menu for picking Edit or Delete mode. Inside a mode the bar takes a tinted
 * container, so it is obvious taps now edit or select, and offers a close button (plus Delete for the selection).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFeedTopBar(mode: FeedMode, selectedCount: Int, canChangeMode: Boolean, onAction: (UserFeedAction) -> Unit) {
    val containerColor by animateColorAsState(
        if (mode ==
            FeedMode.Browse
        ) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    )
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
        title = {
            Text(
                when (mode) {
                    FeedMode.Browse -> stringResource(Res.string.feed_title)

                    FeedMode.Edit -> stringResource(Res.string.feed_title_edit_mode)

                    FeedMode.Delete -> if (selectedCount == 0) {
                        stringResource(Res.string.feed_title_select_users)
                    } else {
                        stringResource(Res.string.feed_title_selected_count, selectedCount)
                    }
                }
            )
        },
        navigationIcon = {
            if (mode != FeedMode.Browse) {
                IconButton(onClick = { onAction(UserFeedAction.OnExitModeClick) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_cancel))
                }
            }
        },
        actions = {
            when (mode) {
                FeedMode.Browse -> FeedOverflowMenu(enabled = canChangeMode, onAction = onAction)

                FeedMode.Edit -> Unit

                FeedMode.Delete -> IconButton(
                    onClick = { onAction(UserFeedAction.OnDeleteSelectedClick) },
                    enabled = selectedCount > 0
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.feed_delete_selected))
                }
            }
        }
    )
}

@Composable
private fun FeedOverflowMenu(enabled: Boolean, onAction: (UserFeedAction) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }, enabled = enabled) {
            // The core icon set has no horizontal "more" icon, so the vertical one is turned on its side.
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.feed_more_options),
                modifier = Modifier.rotate(90f)
            )
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    isExpanded = false
                    onAction(UserFeedAction.OnEditModeClick)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    isExpanded = false
                    onAction(UserFeedAction.OnDeleteModeClick)
                }
            )
        }
    }
}

@Preview
@Composable
private fun UserFeedTopBarPreview() {
    SliideTheme {
        UserFeedTopBar(mode = FeedMode.Browse, selectedCount = 0, canChangeMode = true, onAction = {})
    }
}

@Preview
@Composable
private fun UserFeedTopBarDeleteModePreview() {
    SliideTheme {
        UserFeedTopBar(mode = FeedMode.Delete, selectedCount = 2, canChangeMode = true, onAction = {})
    }
}
