package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.androidlearning.sliide_challenge.core.domain.util.ApplicationScope
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.presentation.FakeUserFeedRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedViewModelTest {

    private class FakeClock : Clock {
        var now = Instant.fromEpochMilliseconds(1_000_000)
        override fun now() = now
    }

    private val repository = FakeUserFeedRepository()
    private val clock = FakeClock()

    /** Unconfined like Main, so deletes sent in it run at once; tied to the test so nothing outlives it. */
    private fun TestScope.applicationScope() =
        ApplicationScope(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = UserFeedViewModel(repository, applicationScope(), clock)

    private val createdAlan = FeedUser(FeedUserId.Created(1), "Alan", "alan@x.io", lastSyncedAt = null)

    @Test
    fun `the first load runs once on creation`() = runTest {
        viewModel()

        assertThat(repository.firstLoadCalls).isEqualTo(1)
    }

    @Test
    fun `empty cache shows the empty state once the first load is done`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.isEmpty).isTrue()
            assertThat(state.loadError).isNull()
        }
    }

    @Test
    fun `an empty feed shimmers while the first page loads`() = runTest {
        val firstLoad = CompletableDeferred<Unit>()
        repository.pendingFirstLoad = firstLoad
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().isLoading).isTrue()

            firstLoad.complete(Unit)
            assertThat(awaitItem().isEmpty).isTrue()
        }
    }

    @Test
    fun `users created on this device show while the first page loads`() = runTest {
        repository.pendingFirstLoad = CompletableDeferred()
        repository.users.value = listOf(createdAlan)
        val viewModel = viewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.users.map { it.id }).containsExactly(FeedUserId.Created(1))
        }
    }

    @Test
    fun `a pull while the first page loads is ignored`() = runTest {
        repository.pendingFirstLoad = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(UserFeedAction.OnRefresh)

        assertThat(repository.loadCalls).isEqualTo(0)
    }

    @Test
    fun `a failed first load shows the reason in the empty state`() = runTest {
        repository.firstLoadResult = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.state.test {
                val state = awaitItem()
                assertThat(state.isEmpty).isTrue()
                assertThat(state.loadError).isEqualTo(DataError.Network.NO_INTERNET)
            }
            expectNoEvents()
        }
        assertThat(repository.loadCalls).isEqualTo(0)
    }

    @Test
    fun `a failed first load over created users shows a snackbar instead`() = runTest {
        repository.users.value = listOf(createdAlan)
        repository.firstLoadResult = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = viewModel()

        viewModel.events.test {
            assertThat(awaitItem()).isEqualTo(UserFeedEvent.RefreshFailed(DataError.Network.NO_INTERNET))
        }
        assertThat(viewModel.state.value.loadError).isNull()
    }

    @Test
    fun `retrying after a failed first load clears the error once a fetch works`() = runTest {
        repository.firstLoadResult = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().loadError).isEqualTo(DataError.Network.NO_INTERNET)

            repository.loadResult = Result.Error(DataError.Network.SERVER_ERROR)
            viewModel.onAction(UserFeedAction.OnRefresh)
            assertThat(expectMostRecentItem().loadError).isEqualTo(DataError.Network.SERVER_ERROR)

            repository.loadResult = Result.Success(Unit)
            viewModel.onAction(UserFeedAction.OnRefresh)
            assertThat(expectMostRecentItem().loadError).isNull()
        }
    }

    @Test
    fun `cached users are shown and updates are reflected`() = runTest {
        val ada = FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", lastSyncedAt = clock.now)
        val alan = FeedUser(FeedUserId.Created(1), "Alan", "alan@x.io", lastSyncedAt = null)
        repository.users.value = listOf(ada)
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().users).isEqualTo(
                listOf(
                    FeedUserUi(
                        FeedUserId.Remote(1),
                        "Ada",
                        "ada@x.io",
                        SyncStatus.Synced(TimeAgo.JustNow),
                        isSynced = true
                    )
                )
            )

            repository.users.value = listOf(alan)
            val updated = awaitItem()
            assertThat(updated.users).isEqualTo(
                listOf(FeedUserUi(FeedUserId.Created(1), "Alan", "alan@x.io", SyncStatus.NotSynced, isSynced = false))
            )
            assertThat(updated.isEmpty).isFalse()
        }
    }

    @Test
    fun `sync times move on each minute while the feed is observed`() = runTest {
        repository.users.value = listOf(FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", lastSyncedAt = clock.now))
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().users.single().syncStatus).isEqualTo(SyncStatus.Synced(TimeAgo.JustNow))

            clock.now += 5.minutes
            advanceTimeBy(1.minutes)
            assertThat(awaitItem().users.single().syncStatus).isEqualTo(SyncStatus.Synced(TimeAgo.Minutes(5)))
        }
    }

    private val ada = FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", lastSyncedAt = null)
    private val alan = FeedUser(FeedUserId.Created(1), "Alan", "alan@x.io", lastSyncedAt = null)

    private fun FeedUser.ui() = toFeedUserUi(clock.now)

    @Test
    fun `long press asks for confirmation and dismissing it deletes nothing`() = runTest {
        repository.users.value = listOf(ada)
        val viewModel = viewModel()

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
        val viewModel = viewModel()

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
    fun `closing the screen sends a delete still waiting on undo`() = runTest {
        repository.users.value = listOf(alan, ada)
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { UserFeedViewModel(repository, applicationScope(), clock) } }
        )[UserFeedViewModel::class]
        viewModel.onAction(UserFeedAction.OnUserLongClick(ada.ui()))
        viewModel.onAction(UserFeedAction.OnDeleteConfirm)
        assertThat(repository.deleteCalls).isEmpty()

        store.clear()

        assertThat(repository.deleteCalls).containsExactly(ada.id)
    }

    @Test
    fun `undo restores the user and never sends the delete`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = viewModel()

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
        val viewModel = viewModel()

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
        val viewModel = viewModel()

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
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteRequested(ada.id))
            expectNoEvents()
        }
    }

    @Test
    fun `tapping a user while browsing opens their details and closing them goes back`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.state.test {
                assertThat(awaitItem().openedUserId).isNull()
                viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
                assertThat(awaitItem().openedUserId).isEqualTo(ada.id)

                // Beside the list another user can be picked straight away.
                viewModel.onAction(UserFeedAction.OnUserClick(alan.ui()))
                assertThat(awaitItem().openedUserId).isEqualTo(alan.id)

                viewModel.onAction(UserFeedAction.OnCloseUserClick)
                assertThat(awaitItem().openedUserId).isNull()
            }
            expectNoEvents()
        }
    }

    @Test
    fun `beside the list the first user is shown until another is picked`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().openedUserId).isNull()
            viewModel.onAction(UserFeedAction.OnLayoutChange(showsDetailsBesideList = true))
            assertThat(awaitItem().openedUserId).isEqualTo(alan.id)

            viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
            assertThat(awaitItem().openedUserId).isEqualTo(ada.id)
        }
    }

    @Test
    fun `over the list nothing opens until a user is tapped`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnLayoutChange(showsDetailsBesideList = true))
            assertThat(awaitItem().openedUserId).isEqualTo(alan.id)

            // Turning a tablet-sized window into a phone-sized one goes back to the list, not to a user never tapped.
            viewModel.onAction(UserFeedAction.OnLayoutChange(showsDetailsBesideList = false))
            assertThat(awaitItem().openedUserId).isNull()
        }
    }

    @Test
    fun `beside the list deleting the shown user shows the next first user`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnLayoutChange(showsDetailsBesideList = true))
            viewModel.onAction(UserFeedAction.OnDeleteRequested(alan.id))
            assertThat(expectMostRecentItem().openedUserId).isEqualTo(ada.id)
        }
    }

    @Test
    fun `beside an empty list nothing is shown`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnLayoutChange(showsDetailsBesideList = true))
            assertThat(expectMostRecentItem().openedUserId).isNull()
        }
    }

    @Test
    fun `tapping a user outside browsing does not open their details`() = runTest {
        repository.users.value = listOf(ada)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnDeleteModeClick)
            viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
            val state = expectMostRecentItem()
            assertThat(state.selectedIds).containsOnly(ada.id)
            assertThat(state.openedUserId).isNull()
        }
    }

    @Test
    fun `deleting the opened user closes their details and undo does not reopen them`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
            viewModel.onAction(UserFeedAction.OnDeleteRequested(ada.id))
            assertThat(expectMostRecentItem().openedUserId).isNull()

            viewModel.onAction(UserFeedAction.OnUndoDeleteClick)
            val restored = expectMostRecentItem()
            assertThat(restored.users).containsExactly(alan.ui(), ada.ui())
            assertThat(restored.openedUserId).isNull()
        }
    }

    @Test
    fun `deleting another user keeps the opened user's details`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
            viewModel.onAction(UserFeedAction.OnUserLongClick(alan.ui()))
            viewModel.onAction(UserFeedAction.OnDeleteConfirm)
            assertThat(expectMostRecentItem().openedUserId).isEqualTo(ada.id)
        }
    }

    @Test
    fun `an opened user who leaves the cache is no longer opened`() = runTest {
        repository.users.value = listOf(alan, ada)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnUserClick(ada.ui()))
            assertThat(awaitItem().openedUserId).isEqualTo(ada.id)

            repository.users.value = listOf(alan)
            assertThat(awaitItem().openedUserId).isNull()
        }
    }

    @Test
    fun `edit mode opens the tapped user and returns to browsing`() = runTest {
        repository.users.value = listOf(ada)
        val viewModel = viewModel()

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
        val viewModel = viewModel()

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
        val viewModel = viewModel()

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
        val viewModel = viewModel()

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
        val viewModel = viewModel()

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
        val viewModel = viewModel()

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

    // refresh

    @Test
    fun `refresh is reported until the fetch finishes`() = runTest {
        repository.users.value = listOf(FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", lastSyncedAt = clock.now))
        val pending = CompletableDeferred<Unit>()
        repository.pendingLoad = pending
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().isRefreshing).isFalse()

            viewModel.onAction(UserFeedAction.OnRefresh)
            assertThat(awaitItem().isRefreshing).isTrue()

            pending.complete(Unit)
            assertThat(awaitItem().isRefreshing).isFalse()
        }
        assertThat(repository.loadCalls).isEqualTo(1)
    }

    @Test
    fun `a pull while refreshing is ignored`() = runTest {
        repository.users.value = listOf(FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", lastSyncedAt = clock.now))
        repository.pendingLoad = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(UserFeedAction.OnRefresh)
            viewModel.onAction(UserFeedAction.OnRefresh)
            assertThat(awaitItem().isRefreshing).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.loadCalls).isEqualTo(1)
    }

    @Test
    fun `a failed refresh keeps the cached users and reports the error once`() = runTest {
        val ada = FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io", lastSyncedAt = clock.now)
        repository.users.value = listOf(ada)
        repository.loadResult = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.state.test {
                awaitItem()
                viewModel.onAction(UserFeedAction.OnRefresh)
                val failed = expectMostRecentItem()
                assertThat(failed.isRefreshing).isFalse()
                assertThat(failed.users.map { it.id }).containsExactly(FeedUserId.Remote(1))
            }
            assertThat(awaitItem()).isEqualTo(UserFeedEvent.RefreshFailed(DataError.Network.NO_INTERNET))
            expectNoEvents()
        }
    }
}
