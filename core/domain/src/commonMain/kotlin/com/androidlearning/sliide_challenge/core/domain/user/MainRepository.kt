package com.androidlearning.sliide_challenge.core.domain.user

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

interface MainRepository {
    /** Emits the most recently loaded users; empty until [loadUsers] succeeds. */
    fun observeUsers(): Flow<List<User>>

    /** Fetches the last page of GoRest `/users` and publishes it to [observeUsers]. */
    suspend fun loadUsers(): EmptyResult<DataError.Network>
}
