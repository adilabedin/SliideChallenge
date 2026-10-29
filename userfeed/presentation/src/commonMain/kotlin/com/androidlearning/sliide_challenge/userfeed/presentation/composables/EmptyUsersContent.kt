package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_empty_message
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.feed_empty_title
import org.jetbrains.compose.resources.stringResource

/** A one-item list rather than a plain column, so pulling down on it still refreshes. */
@Composable
fun EmptyUsersContent(
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(24.dp),
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
                    text = stringResource(Res.string.feed_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.feed_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview
@Composable
private fun EmptyUsersContentPreview() {
    SliideTheme {
        EmptyUsersContent()
    }
}
