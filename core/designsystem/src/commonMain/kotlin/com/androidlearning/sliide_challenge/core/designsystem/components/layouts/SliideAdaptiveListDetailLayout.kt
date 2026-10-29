package com.androidlearning.sliide_challenge.core.designsystem.components.layouts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.core.presentation.util.DeviceConfiguration
import com.androidlearning.sliide_challenge.core.presentation.util.currentDeviceConfiguration

/** Whether [SliideAdaptiveListDetailLayout] shows the list and the detail one at a time rather than side by side. */
val DeviceConfiguration.showsListDetailOnePaneAtATime: Boolean
    get() = when (this) {
        DeviceConfiguration.MOBILE_PORTRAIT -> true
        DeviceConfiguration.MOBILE_LANDSCAPE,
        DeviceConfiguration.TABLET_PORTRAIT,
        DeviceConfiguration.TABLET_LANDSCAPE,
        DeviceConfiguration.DESKTOP -> false
    }

/**
 * A list and the detail of the item picked from it, [selectedItem]. A phone held upright shows one at a time: the
 * detail slides in over the list while an item is selected. Anything wider keeps the list on the start side and the
 * detail beside it, with [emptyDetail] standing in until an item is selected.
 *
 * Each pane is usually a full screen with its own `Scaffold`, so the list's scroll position is kept while it is off
 * screen or moves between the two arrangements, and each pane only pads for the system bars along its own edges.
 */
@Composable
fun <T : Any> SliideAdaptiveListDetailLayout(
    selectedItem: T?,
    list: @Composable () -> Unit,
    detail: @Composable (T) -> Unit,
    emptyDetail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    configuration: DeviceConfiguration = currentDeviceConfiguration(),
) {
    val stateHolder = rememberSaveableStateHolder()
    val savedList: @Composable () -> Unit = { stateHolder.SaveableStateProvider(LIST_STATE_KEY) { list() } }

    if (configuration.showsListDetailOnePaneAtATime) {
        AnimatedContent(
            targetState = selectedItem,
            modifier = modifier.fillMaxSize(),
            // Only moving between the list and a detail animates; the panes themselves handle their own updates.
            contentKey = { it != null },
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 } + fadeOut()
                } else {
                    slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it }
                }
            },
        ) { item ->
            if (item == null) savedList() else detail(item)
        }
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            // A third of a desktop window would make rows needlessly long, and half of a small tablet is too narrow.
            val listWidth = (maxWidth * 0.4f).coerceIn(280.dp, 400.dp)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .width(listWidth)
                        .fillMaxHeight()
                        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
                ) {
                    savedList()
                }
                VerticalDivider()
                AnimatedContent(
                    targetState = selectedItem,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { item ->
                    if (item == null) emptyDetail() else detail(item)
                }
            }
        }
    }
}

private const val LIST_STATE_KEY = "list"

@Composable
private fun PreviewPane(text: String) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview
@Composable
private fun SliideAdaptiveListDetailLayoutMobilePortraitPreview() {
    SliideTheme {
        SliideAdaptiveListDetailLayout(
            selectedItem = null,
            list = { PreviewPane("List") },
            detail = { item: String -> PreviewPane(item) },
            emptyDetail = { PreviewPane("Nothing selected") },
            configuration = DeviceConfiguration.MOBILE_PORTRAIT,
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun SliideAdaptiveListDetailLayoutTabletLandscapePreview() {
    SliideTheme {
        SliideAdaptiveListDetailLayout(
            selectedItem = "Detail",
            list = { PreviewPane("List") },
            detail = { item: String -> PreviewPane(item) },
            emptyDetail = { PreviewPane("Nothing selected") },
            configuration = DeviceConfiguration.TABLET_LANDSCAPE,
        )
    }
}
