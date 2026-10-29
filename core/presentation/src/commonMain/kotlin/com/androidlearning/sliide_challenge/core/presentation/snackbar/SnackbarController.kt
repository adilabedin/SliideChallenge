package com.androidlearning.sliide_challenge.core.presentation.snackbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * The app's one snackbar host, owned by the navigation root so a message outlives the screen that sent it, e.g. "User
 * added" once the form has closed. One snackbar shows at a time; the rest wait their turn.
 */
@Stable
class SnackbarController internal constructor(
    internal val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    internal var snackbarHeight by mutableStateOf(0.dp)

    /** How much of the window's bottom the snackbar covers; screens lift their floating buttons by it. */
    val visibleHeight: Dp get() = if (hostState.currentSnackbarData != null) snackbarHeight else 0.dp

    /** Queues [message] and returns at once, so it still shows if the caller leaves the screen. */
    fun show(message: String) {
        scope.launch { hostState.showSnackbar(message) }
    }

    /** Like [show], but resolves [message] here, so it isn't lost if the caller leaves composition before it loads. */
    fun show(message: StringResource) {
        scope.launch { hostState.showSnackbar(getString(message)) }
    }

    /**
     * Takes down the snackbar showing, for a message that can't wait its turn, such as an Undo whose timer is already
     * running. Suspends until it goes away; cancelling the caller takes it down.
     */
    suspend fun showNow(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ): SnackbarResult {
        hostState.currentSnackbarData?.dismiss()
        return hostState.showSnackbar(message = message, actionLabel = actionLabel, duration = duration)
    }
}

val LocalSnackbarController = staticCompositionLocalOf<SnackbarController> {
    error("No SnackbarController; wrap the content in SnackbarControllerHost")
}

/** Provides [LocalSnackbarController] to [content] and shows its snackbars along the bottom of the window. */
@Composable
fun SnackbarControllerHost(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { SnackbarController(SnackbarHostState(), scope) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalSnackbarController provides controller) {
        Box(Modifier.fillMaxSize()) {
            content()
            SnackbarHost(
                hostState = controller.hostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.onSizeChanged { controller.snackbarHeight = with(density) { it.height.toDp() } },
                )
            }
        }
    }
}
