package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result

interface MainApi {
    /** Page 1 of GoRest `/users`, i.e. the most recently created users. */
    suspend fun getLatestUsers(): Result<List<UserDto>, DataError.Network>
}
