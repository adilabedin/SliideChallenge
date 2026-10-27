package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "User feed coming soon",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview
@Composable
private fun UserFeedScreenPreview() {
    SliideTheme {
        UserFeedScreen(state = UserFeedState())
    }
}
