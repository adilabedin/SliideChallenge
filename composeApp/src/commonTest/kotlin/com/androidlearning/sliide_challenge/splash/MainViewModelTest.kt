package com.androidlearning.sliide_challenge.splash

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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private class FakeMainRepository : MainRepository {
        val results = ArrayDeque<EmptyResult<DataError.Network>>()
        var loadCalls = 0

        override fun observeUsers(): Flow<List<User>> = emptyFlow()

        override suspend fun loadUsers(): EmptyResult<DataError.Network> {
            loadCalls++
            return results.removeFirst()
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeMainRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `is loading on creation so the splash stays up`() = runTest(dispatcher) {
        repository.results += Result.Success(Unit)

        val viewModel = MainViewModel(repository)

        assertThat(viewModel.state.value.isLoading).isTrue()
        assertThat(viewModel.state.value.isReady).isFalse()
    }

    @Test
    fun `successful load becomes ready`() = runTest(dispatcher) {
        repository.results += Result.Success(Unit)
        val viewModel = MainViewModel(repository)

        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(MainState(isLoading = false, error = null))
        assertThat(viewModel.state.value.isReady).isTrue()
    }

    @Test
    fun `failed load stops loading and exposes the error`() = runTest(dispatcher) {
        repository.results += Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = MainViewModel(repository)

        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(
            MainState(isLoading = false, error = DataError.Network.NO_INTERNET),
        )
        assertThat(viewModel.state.value.isReady).isFalse()
    }

    @Test
    fun `retry clears the error then reloads and becomes ready`() = runTest(dispatcher) {
        repository.results += Result.Error(DataError.Network.REQUEST_TIMEOUT)
        repository.results += Result.Success(Unit)
        val viewModel = MainViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(MainAction.OnRetryClick)
        advanceUntilIdle()

        assertThat(repository.loadCalls).isEqualTo(2)
        assertThat(viewModel.state.value.isReady).isTrue()
    }
}
