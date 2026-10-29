package com.androidlearning.sliide_challenge.core.presentation.snackbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarControllerTest {

    private val hostState = SnackbarHostState()

    private val shownMessage: String? get() = hostState.currentSnackbarData?.visuals?.message

    @Test
    fun `shown messages wait their turn`() = runTest {
        val controller = SnackbarController(hostState, backgroundScope)

        controller.show("First")
        controller.show("Second")
        runCurrent()
        assertThat(shownMessage).isEqualTo("First")

        hostState.currentSnackbarData?.dismiss()
        runCurrent()
        assertThat(shownMessage).isEqualTo("Second")
    }

    @Test
    fun `an urgent message takes down the one showing`() = runTest {
        val controller = SnackbarController(hostState, backgroundScope)
        controller.show("Couldn't refresh users")
        runCurrent()

        val undo = async { controller.showNow("Ada deleted", "Undo", SnackbarDuration.Indefinite) }
        runCurrent()
        assertThat(shownMessage).isEqualTo("Ada deleted")

        hostState.currentSnackbarData?.performAction()
        assertThat(undo.await()).isEqualTo(SnackbarResult.ActionPerformed)
    }

    @Test
    fun `cancelling an urgent message takes it down`() = runTest {
        val controller = SnackbarController(hostState, backgroundScope)

        val undo = async { controller.showNow("Ada deleted", "Undo", SnackbarDuration.Indefinite) }
        runCurrent()
        undo.cancel()
        runCurrent()

        assertThat(shownMessage).isNull()
    }

    @Test
    fun `nothing is covered while no snackbar shows`() = runTest {
        val controller = SnackbarController(hostState, backgroundScope)
        controller.snackbarHeight = 48.dp

        assertThat(controller.visibleHeight).isEqualTo(0.dp)

        controller.show("Hello")
        runCurrent()
        assertThat(controller.visibleHeight).isEqualTo(48.dp)
    }
}
