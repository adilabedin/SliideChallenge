package com.androidlearning.sliide_challenge.userfeed.presentation.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.androidlearning.sliide_challenge.userfeed.domain.EmailValidationError
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.FakeUserFeedRepository
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.SyncStatus
import com.androidlearning.sliide_challenge.userfeed.presentation.feed.TimeAgo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class UserDetailViewModelTest {

    private class FakeClock : Clock {
        var now = Instant.fromEpochMilliseconds(1_000_000)
        override fun now() = now
    }

    private val repository = FakeUserFeedRepository()
    private val clock = FakeClock()
    private val savedStateHandle = SavedStateHandle()

    private val ada = FeedUser(FeedUserId.Remote(1), "Ada Lovelace", "ada@example.com", lastSyncedAt = null)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository.users.value = listOf(ada)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(id: FeedUserId = ada.id) = UserDetailViewModel(id, repository, savedStateHandle, clock)

    @Test
    fun `shows the user and follows changes to them`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.isEditing).isFalse()
            assertThat(state.user?.name).isEqualTo("Ada Lovelace")
            assertThat(state.user?.syncStatus).isEqualTo(SyncStatus.NotSynced)

            repository.users.value = listOf(ada.copy(name = "Ada King", lastSyncedAt = clock.now))
            val updated = awaitItem()
            assertThat(updated.user?.name).isEqualTo("Ada King")
            assertThat(updated.user?.syncStatus).isEqualTo(SyncStatus.Synced(TimeAgo.JustNow))
        }
    }

    @Test
    fun `edit prefills the fields with nothing to save yet`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserDetailAction.OnEditClick)
            val editing = awaitItem()
            assertThat(editing.isEditing).isTrue()
            assertThat(editing.name).isEqualTo("Ada Lovelace")
            assertThat(editing.email).isEqualTo("ada@example.com")
            assertThat(editing.nameErrorShown).isNull()
            assertThat(editing.canSave).isFalse()
        }
    }

    @Test
    fun `an invalid change cannot be saved and its error shows once the field is left`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserDetailAction.OnEditClick)
            viewModel.onAction(UserDetailAction.OnEmailChange("ada@example"))
            assertThat(expectMostRecentItem().emailErrorShown).isNull()

            viewModel.onAction(UserDetailAction.OnEmailFocusLost)
            val state = expectMostRecentItem()
            assertThat(state.emailErrorShown).isEqualTo(EmailValidationError.INVALID_FORMAT)
            assertThat(state.canSave).isFalse()

            viewModel.onAction(UserDetailAction.OnSaveClick)
            assertThat(repository.updateCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving sends the normalised change and returns to the details`() = runTest {
        val viewModel = createViewModel()
        repository.pendingSave = CompletableDeferred()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserDetailAction.OnEditClick)
            viewModel.onAction(UserDetailAction.OnNameChange("  Ada   King "))
            assertThat(expectMostRecentItem().canSave).isTrue()

            viewModel.onAction(UserDetailAction.OnSaveClick)
            val saving = expectMostRecentItem()
            assertThat(saving.isSaving).isTrue()
            assertThat(saving.isEditing).isTrue()

            repository.pendingSave?.complete(Unit)
            val saved = expectMostRecentItem()
            assertThat(saved.isSaving).isFalse()
            assertThat(saved.isEditing).isFalse()
            assertThat(repository.updateCalls).containsExactly(Triple(ada.id, "Ada King", "ada@example.com"))
        }
    }

    @Test
    fun `cancelling the edit drops the unsaved change`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserDetailAction.OnEditClick)
            viewModel.onAction(UserDetailAction.OnNameChange("Ada King"))
            viewModel.onAction(UserDetailAction.OnCancelEditClick)
            val state = expectMostRecentItem()
            assertThat(state.isEditing).isFalse()
            assertThat(state.user?.name).isEqualTo("Ada Lovelace")
            assertThat(repository.updateCalls).isEmpty()

            viewModel.onAction(UserDetailAction.OnEditClick)
            assertThat(expectMostRecentItem().name).isEqualTo("Ada Lovelace")
        }
    }

    @Test
    fun `an edit in progress survives process death`() = runTest {
        createViewModel().apply {
            state.test {
                awaitItem()
                onAction(UserDetailAction.OnEditClick)
                onAction(UserDetailAction.OnNameChange("Ada King"))
                cancelAndIgnoreRemainingEvents()
            }
        }

        createViewModel().state.test {
            val restored = awaitItem()
            assertThat(restored.isEditing).isTrue()
            assertThat(restored.name).isEqualTo("Ada King")
        }
    }

    @Test
    fun `confirming the delete hands it back without deleting here`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.state.test {
                awaitItem()
                viewModel.onAction(UserDetailAction.OnDeleteClick)
                assertThat(awaitItem().isDeleteSheetVisible).isTrue()

                viewModel.onAction(UserDetailAction.OnDeleteConfirm)
                assertThat(awaitItem().isDeleteSheetVisible).isFalse()
            }
            assertThat(awaitItem()).isEqualTo(UserDetailEvent.DeleteConfirmed(ada.id))
            assertThat(repository.deleteCalls).isEmpty()
        }
    }

    @Test
    fun `dismissing the delete keeps the user`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserDetailAction.OnDeleteClick)
            viewModel.onAction(UserDetailAction.OnDeleteDismiss)
            val state = expectMostRecentItem()
            assertThat(state.isDeleteSheetVisible).isFalse()
            assertThat(state.user).isNotNull()
        }
    }

    @Test
    fun `closes when the user disappears`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            expectNoEvents()
            repository.users.value = emptyList()
            assertThat(awaitItem()).isEqualTo(UserDetailEvent.UserNotFound)
        }
    }
}
