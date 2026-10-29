package com.androidlearning.sliide_challenge.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Paints a highlight sweeping across the element, the usual stand-in for content that is still loading. The colours
 * come from the theme, so the placeholder sits right in both light and dark mode.
 */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLow
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    drawBehind {
        // The highlight starts fully off the left edge and leaves fully past the right one.
        val bandWidth = size.width / 2
        val start = -bandWidth + progress * (size.width + 2 * bandWidth)
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(start, 0f),
                end = Offset(start + bandWidth, size.height),
            ),
        )
    }
}

/** A rounded shimmering block, sized like the text or image it stands in for. */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .shimmer(),
    )
}
