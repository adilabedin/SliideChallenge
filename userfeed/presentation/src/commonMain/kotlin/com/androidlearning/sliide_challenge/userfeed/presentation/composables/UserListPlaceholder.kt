package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.components.ShimmerBlock
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_loading_users
import org.jetbrains.compose.resources.stringResource

/**
 * Shimmering stand-ins shaped like [UserRow], shown while the feed first reads the database. It doesn't scroll, so
 * a pull can't start before there is a cache to refresh.
 */
@Composable
fun UserListPlaceholder(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(Res.string.feed_loading_users)
    LazyColumn(
        modifier = modifier.semantics { contentDescription = loadingDescription },
        userScrollEnabled = false
    ) {
        items(PLACEHOLDER_ROW_COUNT) {
            UserRowPlaceholder()
            HorizontalDivider()
        }
    }
}

@Composable
private fun UserRowPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.45f), height = 16.dp)
        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.7f), height = 14.dp)
        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.3f), height = 10.dp)
    }
}

/** One GoRest page (`per_page=20`), so the placeholder is as long as the list it stands in for. */
private const val PLACEHOLDER_ROW_COUNT = 20

@Preview
@Composable
private fun UserListPlaceholderPreview() {
    SliideTheme {
        UserListPlaceholder()
    }
}
