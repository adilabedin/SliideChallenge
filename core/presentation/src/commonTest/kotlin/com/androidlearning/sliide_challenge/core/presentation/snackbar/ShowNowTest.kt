package com.androidlearning.sliide_challenge.core.presentation.snackbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ShowNowTest {

    private val hostState = SnackbarHostState()

    private val shownMessage: String? get() = hostState.currentSnackbarData?.visuals?.message

    @Test
    fun `an urgent message takes down the one showing`() = runTest {
        backgroundScope.launch { hostState.showSnackbar("Couldn't refresh users") }
        runCurrent()

        val undo = async { hostState.showNow("Ada deleted", "Undo", SnackbarDuration.Indefinite) }
        runCurrent()
        assertThat(shownMessage).isEqualTo("Ada deleted")

        hostState.currentSnackbarData?.performAction()
        assertThat(undo.await()).isEqualTo(SnackbarResult.ActionPerformed)
    }

    @Test
    fun `cancelling an urgent message takes it down`() = runTest {
        val undo = async { hostState.showNow("Ada deleted", "Undo", SnackbarDuration.Indefinite) }
        runCurrent()
        undo.cancel()
        runCurrent()

        assertThat(shownMessage).isNull()
    }
}
