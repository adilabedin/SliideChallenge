package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result

interface MainApi {
    suspend fun getLastPageUsers(): Result<List<UserDto>, DataError.Network>
}
