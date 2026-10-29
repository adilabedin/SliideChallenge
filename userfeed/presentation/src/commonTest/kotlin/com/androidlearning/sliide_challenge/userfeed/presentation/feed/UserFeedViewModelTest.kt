package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.FakeUserFeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedViewModelTest {

    private class FakeClock : Clock {
        var now = Instant.fromEpochMilliseconds(1_000_000)
        override fun now() = now
    }

    private val repository = FakeUserFeedRepository()
    private val clock = FakeClock()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty cache shows the empty state`() = runTest {
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.isEmpty).isTrue()
        }
    }

    @Test
    fun `cached users are shown and updates are reflected`() = runTest {
        val ada = FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", lastSyncedAt = clock.now)
        val alan = FeedUser(FeedUserId.Created(1), "Alan", "alan@x.io", lastSyncedAt = null)
        repository.users.value = listOf(ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            assertThat(awaitItem().users).isEqualTo(
                listOf(FeedUserUi(FeedUserId.Remote(1), "Ada", "ada@x.io", "Synced just now", isSynced = true)),
            )

            repository.users.value = listOf(alan)
            val updated = awaitItem()
            assertThat(updated.users).isEqualTo(
                listOf(FeedUserUi(FeedUserId.Created(1), "Alan", "alan@x.io", "Not synced", isSynced = false)),
            )
            assertThat(updated.isEmpty).isFalse()
        }
    }

    @Test
    fun `sync times move on each minute while the feed is observed`() = runTest {
        repository.users.value = listOf(FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", lastSyncedAt = clock.now))
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            assertThat(awaitItem().users.single().syncStatus).isEqualTo("Synced just now")

            clock.now += 5.minutes
            advanceTimeBy(1.minutes)
            assertThat(awaitItem().users.single().syncStatus).isEqualTo("Synced 5 minutes ago")
        }
    }

    private val ada = FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", lastSyncedAt = null)
    private val alan = FeedUser(FeedUserId.Created(1), "Alan", "alan@x.io", lastSyncedAt = null)

    private fun FeedUser.ui() = toFeedUserUi(clock.now)

    @Test
    fun `long press asks for confirmation and dismissing it deletes nothing`() = runTest {
        repository.users.value = listOf(ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnUserLongClick(ada.ui()))
            assertThat(awaitItem().usersToDelete).containsExactly(ada.ui())

            viewModel.onAction(UserFeedAction.OnDeleteDismiss)
            val state = awaitItem()
            assertThat(state.usersToDelete).isEmpty()
            assertThat(state.users).containsExactly(ada.ui())
            assertThat(repository.deleteCalls).isEmpty()
        }
    }

    @Test
    fun `confirmed delete hides the user at once and is sent when undo expires`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnUserLongClick(ada.ui()))
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteConfirm)
            val hidden = expectMostRecentItem()
            assertThat(hidden.users).containsExactly(alan.ui())
            assertThat(hidden.usersToDelete).isEmpty()
            assertThat(hidden.deletedUsers).containsExactly(ada.ui())
            assertThat(repository.deleteCalls).isEmpty()

            advanceTimeBy(5.seconds + 1.milliseconds)
            val finished = expectMostRecentItem()
            assertThat(repository.deleteCalls).containsExactly(ada.id)
            assertThat(finished.deletedUsers).isEmpty()
            assertThat(finished.users).containsExactly(alan.ui())
        }
    }

    @Test
    fun `undo restores the user and never sends the delete`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnUserLongClick(alan.ui()))
            viewModel.onAction(UserFeedAction.OnDeleteConfirm)
            viewModel.onAction(UserFeedAction.OnUndoDeleteClick)
            val restored = expectMostRecentItem()
            assertThat(restored.users).containsExactly(alan.ui(), ada.ui())
            assertThat(restored.deletedUsers).isEmpty()

            advanceTimeBy(10.seconds)
            assertThat(repository.deleteCalls).isEmpty()
        }
    }

    @Test
    fun `a second delete sends the first one straight away`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnUserLongClick(alan.ui()))
            viewModel.onAction(UserFeedAction.OnDeleteConfirm)
            viewModel.onAction(UserFeedAction.OnUserLongClick(ada.ui()))
            viewModel.onAction(UserFeedAction.OnDeleteConfirm)
            val state = expectMostRecentItem()
            assertThat(repository.deleteCalls).containsExactly(alan.id)
            assertThat(state.deletedUsers).containsExactly(ada.ui())
            assertThat(state.users).isEmpty()

            viewModel.onAction(UserFeedAction.OnUndoDeleteClick)
            assertThat(expectMostRecentItem().users).containsExactly(ada.ui())
        }
    }

    @Test
    fun `a delete confirmed on another screen gets the same undo window`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteRequested(ada.id))
            val hidden = expectMostRecentItem()
            assertThat(hidden.users).containsExactly(alan.ui())
            assertThat(hidden.deletedUsers).containsExactly(ada.ui())
            assertThat(repository.deleteCalls).isEmpty()

            viewModel.onAction(UserFeedAction.OnUndoDeleteClick)
            assertThat(expectMostRecentItem().users).containsExactly(alan.ui(), ada.ui())

            viewModel.onAction(UserFeedAction.OnDeleteRequested(ada.id))
            advanceTimeBy(5.seconds + 1.milliseconds)
            assertThat(repository.deleteCalls).containsExactly(ada.id)
            assertThat(expectMostRecentItem().deletedUsers).isEmpty()
        }
    }

    @Test
    fun `a delete requested for a user who is already gone does nothing`() = runTest {
        repository.users.value = listOf(alan)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteRequested(ada.id))
            expectNoEvents()
        }
    }

    // The screen opens the user's detail screen itself; the ViewModel has nothing to do.
    @Test
    fun `tapping a user while browsing does nothing`() = runTest {
        repository.users.value = listOf(ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.events.test {
            viewModel.state.test {
                awaitItem()
                viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
                expectNoEvents()
            }
            expectNoEvents()
        }
    }

    @Test
    fun `edit mode opens the tapped user and returns to browsing`() = runTest {
        repository.users.value = listOf(ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.events.test {
            viewModel.state.test {
                awaitItem()
                viewModel.onAction(UserFeedAction.OnEditModeClick)
                assertThat(awaitItem().mode).isEqualTo(FeedMode.Edit)

                viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
                assertThat(awaitItem().mode).isEqualTo(FeedMode.Browse)
            }
            assertThat(awaitItem()).isEqualTo(UserFeedEvent.EditUser(ada.id))
        }
    }

    @Test
    fun `delete mode toggles selection and leaving it clears the selection`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteModeClick)
            viewModel.onAction(UserFeedAction.OnUserClick(alan.ui()))
            viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
            viewModel.onAction(UserFeedAction.OnUserClick(alan.ui()))
            val selecting = expectMostRecentItem()
            assertThat(selecting.mode).isEqualTo(FeedMode.Delete)
            assertThat(selecting.selectedIds).containsOnly(ada.id)

            viewModel.onAction(UserFeedAction.OnExitModeClick)
            val browsing = expectMostRecentItem()
            assertThat(browsing.mode).isEqualTo(FeedMode.Browse)
            assertThat(browsing.selectedIds).isEmpty()
        }
    }

    @Test
    fun `long press only asks to delete while browsing`() = runTest {
        repository.users.value = listOf(ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteModeClick)
            awaitItem()
            viewModel.onAction(UserFeedAction.OnUserLongClick(ada.ui()))
            expectNoEvents()
        }
    }

    @Test
    fun `deleting the selection hides every selected user and undo brings them all back`() = runTest {
        val grace = FeedUser(FeedUserId.Remote(2), "Grace", "grace@x.io", lastSyncedAt = null)
        repository.users.value = listOf(alan, grace, ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteModeClick)
            viewModel.onAction(UserFeedAction.OnUserClick(alan.ui()))
            viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
            viewModel.onAction(UserFeedAction.OnDeleteSelectedClick)
            assertThat(expectMostRecentItem().usersToDelete).containsExactly(alan.ui(), ada.ui())

            viewModel.onAction(UserFeedAction.OnDeleteConfirm)
            val hidden = expectMostRecentItem()
            assertThat(hidden.users).containsExactly(grace.ui())
            assertThat(hidden.deletedUsers).containsExactly(alan.ui(), ada.ui())
            assertThat(hidden.mode).isEqualTo(FeedMode.Browse)
            assertThat(hidden.selectedIds).isEmpty()

            viewModel.onAction(UserFeedAction.OnUndoDeleteClick)
            assertThat(expectMostRecentItem().users).containsExactly(alan.ui(), grace.ui(), ada.ui())
            advanceTimeBy(10.seconds)
            assertThat(repository.deleteCalls).isEmpty()
        }
    }

    @Test
    fun `deleting the selection sends every delete when undo expires`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteModeClick)
            viewModel.onAction(UserFeedAction.OnUserClick(alan.ui()))
            viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
            viewModel.onAction(UserFeedAction.OnDeleteSelectedClick)
            viewModel.onAction(UserFeedAction.OnDeleteConfirm)

            advanceTimeBy(5.seconds + 1.milliseconds)
            val finished = expectMostRecentItem()
            assertThat(repository.deleteCalls).containsExactly(alan.id, ada.id)
            assertThat(finished.users).isEmpty()
            assertThat(finished.deletedUsers).isEmpty()
        }
    }

    @Test
    fun `dismissing the confirmation keeps the selection`() = runTest {
        repository.users.value = listOf(ada)
        val viewModel = UserFeedViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteModeClick)
            viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
            viewModel.onAction(UserFeedAction.OnDeleteSelectedClick)
            viewModel.onAction(UserFeedAction.OnDeleteDismiss)
            val state = expectMostRecentItem()
            assertThat(state.usersToDelete).isEmpty()
            assertThat(state.mode).isEqualTo(FeedMode.Delete)
            assertThat(state.selectedIds).containsOnly(ada.id)
            assertThat(repository.deleteCalls).isEmpty()
        }
    }
}
