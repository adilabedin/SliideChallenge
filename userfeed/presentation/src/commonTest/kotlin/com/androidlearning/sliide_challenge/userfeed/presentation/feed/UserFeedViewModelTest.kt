package com.androidlearning.sliide_challenge.userfeed.presentation.feed

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import com.androidlearning.sliide_challenge.core.domain.user.User
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
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

    private class FakeMainRepository : MainRepository {
        val users = MutableStateFlow<List<User>>(emptyList())

        override fun observeUsers(): Flow<List<User>> = users

        override suspend fun loadUsers(): EmptyResult<DataError.Network> = Result.Success(Unit)
    }

    private val repository = FakeMainRepository()

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
        val ada = User(1, "Ada", "ada@x.io")
        val alan = User(2, "Alan", "alan@x.io")
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
