package com.androidlearning.sliide_challenge.core.domain.user

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

interface MainRepository {
    /** Emits the locally cached users (the source of truth); empty until a load has ever succeeded. */
    fun observeUsers(): Flow<List<User>>

    /** Fetches the last page of GoRest `/users` and replaces the cache with it. On failure the cache is kept. */
    suspend fun loadUsers(): EmptyResult<DataError.Network>
}
