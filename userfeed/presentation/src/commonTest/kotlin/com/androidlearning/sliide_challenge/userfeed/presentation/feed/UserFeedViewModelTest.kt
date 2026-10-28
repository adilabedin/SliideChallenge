package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUser
import com.androidlearning.sliide_challenge.userfeed.domain.FeedUserId
import com.androidlearning.sliide_challenge.userfeed.domain.RejectedCreation
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedViewModelTest {

    private class FakeUserFeedRepository : UserFeedRepository {
        val users = MutableStateFlow<List<FeedUser>>(emptyList())

        override fun observeUsers(): Flow<List<FeedUser>> = users

        override suspend fun createUser(name: String, email: String): EmptyResult<DataError.Network> =
            Result.Success(Unit)

        override suspend fun updateUser(id: FeedUserId, name: String, email: String): EmptyResult<DataError.Network> =
            Result.Success(Unit)

        override suspend fun deleteUser(id: FeedUserId): EmptyResult<DataError.Network> = Result.Success(Unit)

        override fun observeHasPendingSyncs(): Flow<Boolean> = MutableStateFlow(false)

        override suspend fun syncPendingChanges(): EmptyResult<DataError.Network> = Result.Success(Unit)

        override fun observeRejectedCreations(): Flow<List<RejectedCreation>> = MutableStateFlow(emptyList())

        override suspend fun dismissRejectedCreation(id: Long) = Unit
    }

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
}
