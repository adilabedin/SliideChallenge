package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result

interface MainApi {
    /** The final page of GoRest `/users` at `ApiConstants.USERS_PAGE_SIZE` users per page. */
    suspend fun getLastPageUsers(): Result<List<UserDto>, DataError.Network>
}
