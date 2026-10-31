package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme

/** One read-only detail of a user, e.g. their email, with a small [label] above the [value]. */
@Composable
fun UserDetailItem(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified
) {
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        overlineContent = { Text(label) },
        headlineContent = { Text(text = value, color = valueColor) }
    )
}

@Preview
@Composable
private fun UserDetailItemPreview() {
    SliideTheme {
        UserDetailItem(label = "Email", value = "ada@example.com", icon = Icons.Default.Email)
    }
}
