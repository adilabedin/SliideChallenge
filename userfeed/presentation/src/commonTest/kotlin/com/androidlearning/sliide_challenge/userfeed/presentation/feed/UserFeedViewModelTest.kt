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
import com.androidlearning.sliide_challenge.userfeed.domain.UserFeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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
    }

    private val repository = FakeUserFeedRepository()

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
        val viewModel = UserFeedViewModel(repository)

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.isEmpty).isTrue()
        }
    }

    @Test
    fun `cached users are shown and updates are reflected`() = runTest {
        val ada = FeedUser(FeedUserId.Remote(1), "Ada", "ada@x.io")
        val alan = FeedUser(FeedUserId.Created(1), "Alan", "alan@x.io")
        repository.users.value = listOf(ada)
        val viewModel = UserFeedViewModel(repository)

        viewModel.state.test {
            assertThat(awaitItem().users).isEqualTo(listOf(ada))

            repository.users.value = listOf(alan)
            val updated = awaitItem()
            assertThat(updated.users).isEqualTo(listOf(alan))
            assertThat(updated.isEmpty).isFalse()
        }
    }
}
