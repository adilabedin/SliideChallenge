package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.database.user.UserDao
import com.androidlearning.sliide_challenge.core.database.user.UserEntity
import com.androidlearning.sliide_challenge.core.domain.user.MainRepository
import com.androidlearning.sliide_challenge.core.domain.user.User
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.asEmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.map
import com.androidlearning.sliide_challenge.core.domain.util.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room is the single source of truth: [observeUsers] always reads the cache, and [loadUsers]
 * replaces the cache with the last GoRest page. A failed fetch leaves the cache untouched.
 */
class OfflineFirstMainRepository(
    private val mainApi: MainApi,
    private val userDao: UserDao,
) : MainRepository {

    override fun observeUsers(): Flow<List<User>> =
        userDao.getAll().map { entities -> entities.map(UserEntity::toDomain) }

    override suspend fun loadUsers(): EmptyResult<DataError.Network> {
        return mainApi.getLastPageUsers()
            .map { dtos -> dtos.map { it.toDomain().toEntity() } }
            .onSuccess { entities -> userDao.replaceAll(entities) }
            .asEmptyResult()
    }
}
