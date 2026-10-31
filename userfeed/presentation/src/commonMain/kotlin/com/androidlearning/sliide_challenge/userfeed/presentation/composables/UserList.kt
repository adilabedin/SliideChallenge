package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.FeedMode
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.FeedUserUi
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.SyncStatus
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.TimeAgo
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.listKey
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res

@Composable
fun UserList(
    users: List<FeedUserUi>,
    mode: FeedMode,
    selectedIds: Set<FeedUserId>,
    openedUserId: FeedUserId?,
    listState: LazyListState,
    onUserClick: (FeedUserUi) -> Unit,
    onUserLongClick: (FeedUserUi) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollToTopOnUserCreated(users = users, listState = listState)
    LazyColumn(
        state = listState,
        modifier = modifier,
        // Room for the FAB, so it never covers the last row.
        contentPadding = PaddingValues(bottom = 88.dp)
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
                    onLongClick = { onUserLongClick(user) }
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
private fun ScrollToTopOnUserCreated(users: List<FeedUserUi>, listState: LazyListState) {
    val newestCreatedId = users.maxOfOrNull { (it.id as? FeedUserId.Created)?.localId ?: 0L } ?: 0L
    var highestSeenId by rememberSaveable { mutableLongStateOf(newestCreatedId) }
    LaunchedEffect(newestCreatedId) {
        if (newestCreatedId > highestSeenId) {
            listState.animateScrollToItem(0)
            highestSeenId = newestCreatedId
        }
    }
}

@Preview
@Composable
private fun UserListPreview() {
    SliideTheme {
        UserList(
            users = listOf(
                FeedUserUi(FeedUserId.Created(1), "Grace Hopper", "grace@example.com", SyncStatus.NotSynced, false),
                FeedUserUi(
                    FeedUserId.Remote(1),
                    "Ada Lovelace",
                    "ada@example.com",
                    SyncStatus.Synced(TimeAgo.Minutes(5)),
                    true
                )
            ),
            mode = FeedMode.Browse,
            selectedIds = emptySet(),
            openedUserId = FeedUserId.Remote(1),
            listState = rememberLazyListState(),
            onUserClick = {},
            onUserLongClick = {}
        )
    }
}
