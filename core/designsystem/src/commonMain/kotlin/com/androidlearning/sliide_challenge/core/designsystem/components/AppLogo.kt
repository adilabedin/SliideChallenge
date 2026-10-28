package com.androidlearning.sliide_challenge.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.androidlearning.sliide_challenge.core.designsystem.resources.Res
import com.androidlearning.sliide_challenge.core.designsystem.resources.app_logo
import org.jetbrains.compose.resources.painterResource

/** White logo glyph on a transparent background; same artwork as the native launch screens. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.app_logo),
        contentDescription = null,
        modifier = modifier,
    )
}
