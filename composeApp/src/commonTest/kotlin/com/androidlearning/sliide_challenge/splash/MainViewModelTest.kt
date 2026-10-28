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
import kotlinx.coroutines.flow.flowOf
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
        var result: EmptyResult<DataError.Network> = Result.Success(Unit)
        var loadCalls = 0
        var cachedUsers: List<User> = emptyList()

        override fun observeUsers(): Flow<List<User>> = flowOf(cachedUsers)

        override suspend fun loadUsers(): EmptyResult<DataError.Network> {
            loadCalls++
            return result
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
        val viewModel = MainViewModel(repository)

        assertThat(viewModel.isLoading.value).isTrue()
    }

    @Test
    fun `empty cache loads users from the network then stops loading`() = runTest(dispatcher) {
        val viewModel = MainViewModel(repository)

        advanceUntilIdle()

        assertThat(repository.loadCalls).isEqualTo(1)
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `cached users skip the network`() = runTest(dispatcher) {
        repository.cachedUsers = listOf(User(1, "Ada", "ada@x.io"))
        val viewModel = MainViewModel(repository)

        advanceUntilIdle()

        assertThat(repository.loadCalls).isEqualTo(0)
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `failed load still stops loading so the feed can show its empty state`() = runTest(dispatcher) {
        repository.result = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = MainViewModel(repository)

        advanceUntilIdle()

        assertThat(viewModel.isLoading.value).isFalse()
    }
}
