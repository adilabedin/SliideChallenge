package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.SyncStatus
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.asString
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.Res
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.detail_sync_status
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.field_email
import com.androidlearning.sliide_challenge.userfeed.presentation.resources.field_name
import org.jetbrains.compose.resources.stringResource

/** A user's saved details as a read-only card; [syncStatus] is tinted while a change still waits to sync. */
@Composable
fun UserDetails(
    name: String,
    email: String,
    syncStatus: String,
    isSynced: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            UserDetailItem(label = stringResource(Res.string.field_name), value = name, icon = Icons.Default.Person)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            UserDetailItem(label = stringResource(Res.string.field_email), value = email, icon = Icons.Default.Email)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            UserDetailItem(
                label = stringResource(Res.string.detail_sync_status),
                value = syncStatus,
                icon = Icons.Default.Refresh,
                valueColor = if (isSynced) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Preview
@Composable
private fun UserDetailsPreview() {
    SliideTheme {
        UserDetails(name = "Ada Lovelace", email = "ada@example.com", syncStatus = SyncStatus.NotSynced.asString(), isSynced = false)
    }
}
