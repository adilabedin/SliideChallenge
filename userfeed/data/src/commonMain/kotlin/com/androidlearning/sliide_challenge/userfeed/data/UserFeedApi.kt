package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result

interface UserFeedApi {
    /** The final page of GoRest `/users` at `ApiConstants.USERS_PAGE_SIZE` users per page. */
    suspend fun getLastPageUsers(): Result<List<UserDto>, DataError.Network>

    /** `POST /users`; succeeds on 201 with the created user, including its GoRest id. */
    suspend fun createUser(name: String, email: String): Result<UserDto, DataError.Network>

    /** `PATCH /users/{id}`. */
    suspend fun updateUser(id: Long, name: String, email: String): Result<UserDto, DataError.Network>

    /** `GET /users?email=`; the user whose email matches exactly (ignoring case), or null if there is none. */
    suspend fun findUserByEmail(email: String): Result<UserDto?, DataError.Network>

    /** `DELETE /users/{id}`; succeeds on 204, which has no body. */
    suspend fun deleteUser(id: Long): EmptyResult<DataError.Network>
}
