package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.data.networking.ApiConstants
import com.androidlearning.sliide_challenge.core.data.networking.delete
import com.androidlearning.sliide_challenge.core.data.networking.patch
import com.androidlearning.sliide_challenge.core.data.networking.post
import com.androidlearning.sliide_challenge.core.data.user.UserDto
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import io.ktor.client.HttpClient

class KtorUserFeedApi(
    private val httpClient: HttpClient,
) : UserFeedApi {

    override suspend fun createUser(name: String, email: String): Result<UserDto, DataError.Network> =
        httpClient.post(
            route = ApiConstants.USERS_ROUTE,
            body = CreateUserRequestDto(
                name = name,
                email = email,
                gender = CreateUserRequestDto.DEFAULT_GENDER,
                status = CreateUserRequestDto.DEFAULT_STATUS,
            ),
        )

    override suspend fun updateUser(id: Long, name: String, email: String): Result<UserDto, DataError.Network> =
        httpClient.patch(
            route = userRoute(id),
            body = UpdateUserRequestDto(name = name, email = email),
        )

    override suspend fun deleteUser(id: Long): EmptyResult<DataError.Network> =
        httpClient.delete(route = userRoute(id))

    private fun userRoute(id: Long) = "${ApiConstants.USERS_ROUTE}/$id"
}
