package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.data.user.UserDto
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result

interface UserFeedApi {
    /** `POST /users`; succeeds on 201 with the created user, including its GoRest id. */
    suspend fun createUser(name: String, email: String): Result<UserDto, DataError.Network>

    /** `PATCH /users/{id}`. */
    suspend fun updateUser(id: Long, name: String, email: String): Result<UserDto, DataError.Network>

    /** `GET /users?email=`; the user whose email matches exactly (ignoring case), or null if there is none. */
    suspend fun findUserByEmail(email: String): Result<UserDto?, DataError.Network>

    /** `DELETE /users/{id}`; succeeds on 204, which has no body. */
    suspend fun deleteUser(id: Long): EmptyResult<DataError.Network>
}
