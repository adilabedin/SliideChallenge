package com.androidlearning.sliide_challenge.userfeed.presentation.composables

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.userfeed.domain.UserValidator

/** The initials of up to the first two words of [name], or a person icon while there are none; fades as they change. */
@Composable
fun UserAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    val initials = UserValidator.normalizeName(name)
        .split(' ')
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.size(88.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Crossfade(targetState = initials) { text ->
                if (text.isEmpty()) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(44.dp))
                } else {
                    Text(text = text, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Preview
@Composable
private fun UserAvatarPreview() {
    SliideTheme {
        UserAvatar(name = "Ada Lovelace")
    }
}

@Preview
@Composable
private fun UserAvatarEmptyPreview() {
    SliideTheme {
        UserAvatar(name = "")
    }
}
