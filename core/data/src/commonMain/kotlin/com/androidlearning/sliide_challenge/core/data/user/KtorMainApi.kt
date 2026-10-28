package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.data.networking.ApiConstants
import com.androidlearning.sliide_challenge.core.data.networking.get
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import io.ktor.client.HttpClient

class KtorMainApi(
    private val httpClient: HttpClient,
) : MainApi {

    /**
     * GoRest lists users newest first, so the default first page holds the latest users. Users
     * created from the app land here too, so a refresh keeps them.
     */
    override suspend fun getLatestUsers(): Result<List<UserDto>, DataError.Network> =
        httpClient.get<List<UserDto>>(route = ApiConstants.USERS_ROUTE)
}
