package com.androidlearning.sliide_challenge.core.data.user

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.domain.user.Gender
import com.androidlearning.sliide_challenge.core.domain.user.User
import com.androidlearning.sliide_challenge.core.domain.user.UserStatus
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KtorMainRepositoryTest {

    private class FakeMainApi : MainApi {
        var result: Result<List<UserDto>, DataError.Network> = Result.Success(emptyList())
        override suspend fun getLastPageUsers() = result
    }

    private val api = FakeMainApi()
    private val repository = KtorMainRepository(api)

    @Test
    fun `successful load publishes mapped users`() = runTest {
        api.result = Result.Success(listOf(UserDto(1, "Ada", "ada@x.io", "female", "active")))

        repository.observeUsers().test {
            assertThat(awaitItem()).isEmpty()

            assertThat(repository.loadUsers()).isEqualTo(Result.Success(Unit))
            assertThat(awaitItem()).isEqualTo(
                listOf(User(1, "Ada", "ada@x.io", Gender.FEMALE, UserStatus.ACTIVE)),
            )
        }
    }

    @Test
    fun `failed load returns the error and keeps previous users`() = runTest {
        api.result = Result.Success(listOf(UserDto(1, "Ada", "ada@x.io", "female", "active")))
        repository.loadUsers()

        api.result = Result.Error(DataError.Network.NO_INTERNET)
        val result = repository.loadUsers()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        repository.observeUsers().test {
            assertThat(awaitItem().map { it.id }).isEqualTo(listOf(1L))
        }
    }
}
