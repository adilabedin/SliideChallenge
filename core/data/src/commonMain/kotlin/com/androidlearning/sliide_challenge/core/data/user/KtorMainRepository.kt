package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import com.androidlearning.sliide_challenge.core.domain.user.User
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.asEmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.map
import com.androidlearning.sliide_challenge.core.domain.util.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds loaded users in memory until the Room cache replaces it as the source of truth. */
class KtorMainRepository(
    private val mainApi: MainApi,
) : MainRepository {

    private val users = MutableStateFlow<List<User>>(emptyList())

    override fun observeUsers(): Flow<List<User>> = users.asStateFlow()

    override suspend fun loadUsers(): EmptyResult<DataError.Network> {
        return mainApi.getLastPageUsers()
            .map { dtos -> dtos.map { it.toDomain() } }
            .onSuccess { users.value = it }
            .asEmptyResult()
    }
}
