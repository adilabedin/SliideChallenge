package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserFeedRoot(
    viewModel: UserFeedViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UserFeedScreen(state = state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFeedScreen(
    state: UserFeedState,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Users") }) },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when {
            state.isLoading -> Box(modifier)
            state.isEmpty -> EmptyUsersContent(modifier)
            else -> UserList(users = state.users, modifier = modifier)
        }
    }
}

@Composable
private fun UserList(
    users: List<FeedUserUi>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(items = users, key = { it.id.listKey() }) { user ->
            ListItem(
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
            )
            HorizontalDivider()
        }
    }
}

/** Lazy-list keys must be saveable on Android, so the sealed id is flattened to a string unique across both kinds. */
private fun FeedUserId.listKey(): String =
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
        )
    }
}

@Preview
@Composable
private fun UserFeedScreenEmptyPreview() {
    SliideTheme {
        UserFeedScreen(state = UserFeedState(isLoading = false))
    }
}
