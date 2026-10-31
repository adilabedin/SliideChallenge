package com.androidlearning.sliide_challenge.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// "Midnight" palette: cool neutral surfaces, a deep navy/steel accent and a muted bronze tertiary.
// Every role is set explicitly so nothing falls back to Material's baseline purple/pink.

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E2A44),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B3A5C),
    onPrimaryContainer = Color(0xFFE3E8F4),
    inversePrimary = Color(0xFFB4C3E6),
    secondary = Color(0xFF4A5468),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE2EE),
    onSecondaryContainer = Color(0xFF141B2A),
    tertiary = Color(0xFF8A5A12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5DFBF),
    onTertiaryContainer = Color(0xFF2C1A00),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF15181E),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF15181E),
    surfaceVariant = Color(0xFFE1E4EA),
    onSurfaceVariant = Color(0xFF454B57),
    surfaceTint = Color(0xFF1E2A44),
    inverseSurface = Color(0xFF2A2E35),
    inverseOnSurface = Color(0xFFEEF0F4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF757B87),
    outlineVariant = Color(0xFFC5CAD3),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF6F7F9),
    surfaceDim = Color(0xFFD6DAE0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F2F5),
    surfaceContainer = Color(0xFFEAEDF1),
    surfaceContainerHigh = Color(0xFFE4E7EC),
    surfaceContainerHighest = Color(0xFFDEE2E8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C3E6),
    onPrimary = Color(0xFF1A2742),
    primaryContainer = Color(0xFF2B3A5C),
    onPrimaryContainer = Color(0xFFDCE4F7),
    inversePrimary = Color(0xFF1E2A44),
    secondary = Color(0xFFBEC6D6),
    onSecondary = Color(0xFF283040),
    secondaryContainer = Color(0xFF2A3240),
    onSecondaryContainer = Color(0xFFDCE2EE),
    tertiary = Color(0xFFE3BB7A),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF654214),
    onTertiaryContainer = Color(0xFFF5DFBF),
    background = Color(0xFF0D0F13),
    onBackground = Color(0xFFE3E5EA),
    surface = Color(0xFF0D0F13),
    onSurface = Color(0xFFE3E5EA),
    surfaceVariant = Color(0xFF2C313A),
    onSurfaceVariant = Color(0xFFA9B0BC),
    surfaceTint = Color(0xFFB4C3E6),
    inverseSurface = Color(0xFFE3E5EA),
    inverseOnSurface = Color(0xFF2A2E35),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF7A818D),
    outlineVariant = Color(0xFF333945),
    scrim = Color.Black,
    surfaceBright = Color(0xFF30343C),
    surfaceDim = Color(0xFF0D0F13),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF14171C),
    surfaceContainer = Color(0xFF181B21),
    surfaceContainerHigh = Color(0xFF1F232A),
    surfaceContainerHighest = Color(0xFF272B33)
)

@Composable
fun SliideTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
