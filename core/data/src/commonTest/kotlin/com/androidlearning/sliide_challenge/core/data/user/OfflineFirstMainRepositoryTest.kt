package com.androidlearning.sliide_challenge.core.data.user

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.database.user.UserDao
import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.core.domain.user.User
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OfflineFirstMainRepositoryTest {

    private class FakeMainApi : MainApi {
        var result: Result<List<UserDto>, DataError.Network> = Result.Success(emptyList())
        override suspend fun getLastPageUsers() = result
    }

    private class FakeUserDao : UserDao {
        val rows = MutableStateFlow<Map<Long, UserEntity>>(emptyMap())

        override suspend fun insertAll(users: List<UserEntity>) {
            rows.value += users.associateBy { it.id }
        }

        override suspend fun deleteAll() {
            rows.value = emptyMap()
        }

        override suspend fun replaceAll(users: List<UserEntity>) {
            rows.value = users.associateBy { it.id }
        }

        override fun getAll(): Flow<List<UserEntity>> =
            rows.map { it.values.sortedByDescending(UserEntity::id) }
    }

    private val api = FakeMainApi()
    private val dao = FakeUserDao()
    private val repository = OfflineFirstMainRepository(api, dao)

    private val ada = UserDto(1, "Ada", "ada@x.io")
    private val alan = UserDto(2, "Alan", "alan@x.io")

    @Test
    fun `successful load writes the page to the database and observers see it`() = runTest {
        api.result = Result.Success(listOf(ada))

        repository.observeUsers().test {
            assertThat(awaitItem()).isEmpty()

            assertThat(repository.loadUsers()).isEqualTo(Result.Success(Unit))
            assertThat(awaitItem()).isEqualTo(
                listOf(User(1, "Ada", "ada@x.io")),
            )
        }
    }

    @Test
    fun `successful load replaces the previously cached page`() = runTest {
        api.result = Result.Success(listOf(ada))
        repository.loadUsers()

        api.result = Result.Success(listOf(alan))
        repository.loadUsers()

        assertThat(repository.observeUsers().first().map { it.id }).isEqualTo(listOf(2L))
    }

    @Test
    fun `failed load returns the error and keeps cached users`() = runTest {
        api.result = Result.Success(listOf(ada))
        repository.loadUsers()

        api.result = Result.Error(DataError.Network.NO_INTERNET)
        val result = repository.loadUsers()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(repository.observeUsers().first().map { it.id }).isEqualTo(listOf(1L))
    }
}
