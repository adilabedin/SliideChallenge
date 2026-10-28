package com.androidlearning.sliide_challenge.userfeed.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.EmailValidationError
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.NameValidationError
import com.androidlearning.sliide_challenge.userfeed.presentation.FakeUserFeedRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserFormViewModelTest {

    private val repository = FakeUserFeedRepository()
    private val savedStateHandle = SavedStateHandle()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(mode: UserFormMode = UserFormMode.Create) =
        UserFormViewModel(mode, repository, savedStateHandle)

    private fun UserFormViewModel.fill(name: String, email: String) {
        onAction(UserFormAction.OnNameChange(name))
        onAction(UserFormAction.OnEmailChange(email))
    }

    @Test
    fun `a new form shows no errors and cannot be submitted`() {
        val state = createViewModel().state.value

        assertThat(state.isEditing).isFalse()
        assertThat(state.nameErrorShown).isNull()
        assertThat(state.emailErrorShown).isNull()
        assertThat(state.canSubmit).isFalse()
    }

    @Test
    fun `valid details can be submitted`() {
        val viewModel = createViewModel()

        viewModel.fill("Ada Lovelace", "ada@example.com")

        val state = viewModel.state.value
        assertThat(state.isNameValid).isTrue()
        assertThat(state.isEmailValid).isTrue()
        assertThat(state.canSubmit).isTrue()
    }

    @Test
    fun `an error is hidden while typing and shown once the edited field is left`() {
        val viewModel = createViewModel()

        viewModel.onAction(UserFormAction.OnEmailChange("ada@"))
        assertThat(viewModel.state.value.emailError).isEqualTo(EmailValidationError.INVALID_FORMAT)
        assertThat(viewModel.state.value.emailErrorShown).isNull()

        viewModel.onAction(UserFormAction.OnEmailFocusLost)
        assertThat(viewModel.state.value.emailErrorShown).isEqualTo(EmailValidationError.INVALID_FORMAT)
    }

    @Test
    fun `once shown an error follows every keystroke`() {
        val viewModel = createViewModel()
        viewModel.onAction(UserFormAction.OnNameChange("A"))
        viewModel.onAction(UserFormAction.OnNameFocusLost)
        assertThat(viewModel.state.value.nameErrorShown).isEqualTo(NameValidationError.TOO_SHORT)

        viewModel.onAction(UserFormAction.OnNameChange("A1"))
        assertThat(viewModel.state.value.nameErrorShown).isEqualTo(NameValidationError.INVALID_CHARACTERS)

        viewModel.onAction(UserFormAction.OnNameChange("Al"))
        assertThat(viewModel.state.value.nameErrorShown).isNull()

        viewModel.onAction(UserFormAction.OnNameChange(""))
        assertThat(viewModel.state.value.nameErrorShown).isEqualTo(NameValidationError.EMPTY)
    }

    @Test
    fun `leaving a field without typing in it shows no error`() {
        val viewModel = createViewModel()

        viewModel.onAction(UserFormAction.OnNameFocusLost)
        viewModel.onAction(UserFormAction.OnEmailFocusLost)

        assertThat(viewModel.state.value.nameErrorShown).isNull()
        assertThat(viewModel.state.value.emailErrorShown).isNull()
    }

    @Test
    fun `surrounding spaces do not make a field invalid`() {
        val viewModel = createViewModel()

        viewModel.fill("  Ada  ", " ada@example.com ")

        assertThat(viewModel.state.value.canSubmit).isTrue()
    }

    @Test
    fun `a name of only spaces is empty`() {
        val viewModel = createViewModel()

        viewModel.onAction(UserFormAction.OnNameChange("   "))

        assertThat(viewModel.state.value.nameError).isEqualTo(NameValidationError.EMPTY)
    }

    @Test
    fun `submitting creates the user with normalised details and closes the form`() = runTest {
        val viewModel = createViewModel()
        viewModel.fill("  Ada   Lovelace ", " ada@example.com ")

        viewModel.events.test {
            viewModel.onAction(UserFormAction.OnSubmitClick)

            assertThat(awaitItem()).isEqualTo(UserFormEvent.Saved)
        }
        assertThat(repository.createCalls).containsExactly("Ada Lovelace" to "ada@example.com")
    }

    @Test
    fun `submitting an invalid form saves nothing and reveals both errors`() {
        val viewModel = createViewModel()

        viewModel.onAction(UserFormAction.OnSubmitClick)

        assertThat(repository.createCalls).isEmpty()
        assertThat(viewModel.state.value.nameErrorShown).isEqualTo(NameValidationError.EMPTY)
        assertThat(viewModel.state.value.emailErrorShown).isEqualTo(EmailValidationError.EMPTY)
    }

    @Test
    fun `the form is locked while saving and a second submit is ignored`() = runTest {
        val pendingSave = CompletableDeferred<Unit>()
        repository.pendingSave = pendingSave
        val viewModel = createViewModel()
        viewModel.fill("Ada", "ada@example.com")

        viewModel.onAction(UserFormAction.OnSubmitClick)
        viewModel.onAction(UserFormAction.OnSubmitClick)

        assertThat(viewModel.state.value.isSaving).isTrue()
        assertThat(viewModel.state.value.canSubmit).isFalse()
        assertThat(repository.createCalls.size).isEqualTo(1)

        viewModel.events.test {
            pendingSave.complete(Unit)
            assertThat(awaitItem()).isEqualTo(UserFormEvent.Saved)
        }
    }

    @Test
    fun `a save in flight still finishes after the form is closed`() {
        val pendingSave = CompletableDeferred<Unit>()
        repository.pendingSave = pendingSave
        repository.nextResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)
        val viewModel = createViewModel()
        viewModel.fill("Ada", "ada@example.com")
        viewModel.onAction(UserFormAction.OnSubmitClick)

        viewModel.viewModelScope.cancel()
        pendingSave.complete(Unit)

        assertThat(repository.completedSaves).isEqualTo(1)
        // The follow-up after the repository call (dropping the rejection record) also ran.
        assertThat(repository.rejectedCreations.value).isEmpty()
    }

    @Test
    fun `an email GoRest refuses keeps the form open with the error and drops the rejection record`() = runTest {
        repository.nextResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)
        val viewModel = createViewModel()
        viewModel.fill("Ada", "taken@example.com")

        viewModel.events.test {
            viewModel.onAction(UserFormAction.OnSubmitClick)
            expectNoEvents()
        }

        val state = viewModel.state.value
        assertThat(state.isSaving).isFalse()
        assertThat(state.isEmailTaken).isTrue()
        assertThat(state.canSubmit).isFalse()
        assertThat(repository.rejectedCreations.value).isEmpty()
    }

    @Test
    fun `changing a refused email clears the error`() {
        repository.nextResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)
        val viewModel = createViewModel()
        viewModel.fill("Ada", "taken@example.com")
        viewModel.onAction(UserFormAction.OnSubmitClick)

        viewModel.onAction(UserFormAction.OnEmailChange("free@example.com"))

        assertThat(viewModel.state.value.isEmailTaken).isFalse()
        assertThat(viewModel.state.value.canSubmit).isTrue()
    }

    @Test
    fun `other failures were queued for sync so the form still closes`() = runTest {
        repository.nextResult = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = createViewModel()
        viewModel.fill("Ada", "ada@example.com")

        viewModel.events.test {
            viewModel.onAction(UserFormAction.OnSubmitClick)

            assertThat(awaitItem()).isEqualTo(UserFormEvent.Saved)
        }
    }

    @Test
    fun `typed details are kept in the saved state`() {
        val viewModel = createViewModel()

        viewModel.fill("Ada", "ada@example.com")

        assertThat(savedStateHandle.get<String>("name")).isEqualTo("Ada")
        assertThat(savedStateHandle.get<String>("email")).isEqualTo("ada@example.com")
    }

    @Test
    fun `details are restored from the saved state`() {
        savedStateHandle["name"] = "Ada"
        savedStateHandle["email"] = "ada@example.com"

        val state = createViewModel().state.value

        assertThat(state.name).isEqualTo("Ada")
        assertThat(state.email).isEqualTo("ada@example.com")
        assertThat(state.canSubmit).isTrue()
    }

    @Test
    fun `editing prefills the user's current details`() {
        val id = FeedUserId.Remote(7)
        repository.users.value = listOf(FeedUser(id, "Grace Hopper", "grace@example.com", lastSyncedAt = null))

        val state = createViewModel(UserFormMode.Edit(id)).state.value

        assertThat(state.isEditing).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.name).isEqualTo("Grace Hopper")
        assertThat(state.email).isEqualTo("grace@example.com")
        assertThat(state.nameErrorShown).isNull()
    }

    @Test
    fun `editing after process death keeps the restored details instead of the stored ones`() {
        val id = FeedUserId.Created(3)
        repository.users.value = listOf(FeedUser(id, "Grace Hopper", "grace@example.com", lastSyncedAt = null))
        savedStateHandle["name"] = "Grace B. Hopper"
        savedStateHandle["email"] = "grace@example.com"

        val state = createViewModel(UserFormMode.Edit(id)).state.value

        assertThat(state.name).isEqualTo("Grace B. Hopper")
    }

    @Test
    fun `saving an edit updates that user and closes the form`() = runTest {
        val id = FeedUserId.Created(3)
        repository.users.value = listOf(FeedUser(id, "Grace Hopper", "grace@example.com", lastSyncedAt = null))
        val viewModel = createViewModel(UserFormMode.Edit(id))
        viewModel.onAction(UserFormAction.OnNameChange("Grace B. Hopper"))

        viewModel.events.test {
            viewModel.onAction(UserFormAction.OnSubmitClick)

            assertThat(awaitItem()).isEqualTo(UserFormEvent.Saved)
        }
        assertThat(repository.updateCalls).containsExactly(Triple(id, "Grace B. Hopper", "grace@example.com"))
        assertThat(repository.createCalls).isEmpty()
    }

    @Test
    fun `an edit GoRest refuses was queued for sync so the form still closes`() = runTest {
        val id = FeedUserId.Remote(7)
        repository.users.value = listOf(FeedUser(id, "Grace Hopper", "grace@example.com", lastSyncedAt = null))
        repository.nextResult = Result.Error(DataError.Network.UNPROCESSABLE_ENTITY)
        val viewModel = createViewModel(UserFormMode.Edit(id))

        viewModel.events.test {
            viewModel.onAction(UserFormAction.OnSubmitClick)

            assertThat(awaitItem()).isEqualTo(UserFormEvent.Saved)
        }
    }

    @Test
    fun `editing a user that no longer exists closes the form`() = runTest {
        val viewModel = createViewModel(UserFormMode.Edit(FeedUserId.Remote(99)))

        viewModel.events.test {
            assertThat(awaitItem()).isEqualTo(UserFormEvent.UserNotFound)
        }
    }
}
