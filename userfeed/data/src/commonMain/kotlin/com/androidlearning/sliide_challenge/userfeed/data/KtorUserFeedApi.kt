package com.androidlearning.sliide_challenge.userfeed.data

import com.androidlearning.sliide_challenge.core.data.networking.ApiConstants
import com.androidlearning.sliide_challenge.core.data.networking.NetworkResponse
import com.androidlearning.sliide_challenge.core.data.networking.delete
import com.androidlearning.sliide_challenge.core.data.networking.get
import com.androidlearning.sliide_challenge.core.data.networking.patch
import com.androidlearning.sliide_challenge.core.data.networking.post
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.EmptyResult
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.core.domain.util.map
import io.ktor.client.HttpClient

class KtorUserFeedApi(private val httpClient: HttpClient) : UserFeedApi {

    /**
     * GoRest has no "last page" link or reverse sort; the page count only arrives in the
     * `x-pagination-pages` header. So: request page 1 to read the count, then request that page.
     * Either request failing fails the whole call.
     */
    override suspend fun getLastPageUsers(): Result<List<UserDto>, DataError.Network> {
        val firstPage = when (val result = fetchPage(1)) {
            is Result.Error -> return result
            is Result.Success -> result.data
        }
        // An empty dataset reports 0 pages; page 1 is then the (empty) last page.
        val lastPage = firstPage.pageCount()?.coerceAtLeast(1)
            ?: return Result.Error(DataError.Network.UNKNOWN)
        if (lastPage == 1) return Result.Success(firstPage.body)

        return fetchPage(lastPage).map { it.body }
    }

    override suspend fun createUser(name: String, email: String): Result<UserDto, DataError.Network> = httpClient.post(
        route = ApiConstants.USERS_ROUTE,
        body = CreateUserRequestDto(
            name = name,
            email = email,
            gender = CreateUserRequestDto.DEFAULT_GENDER,
            status = CreateUserRequestDto.DEFAULT_STATUS
        )
    )

    override suspend fun updateUser(id: Long, name: String, email: String): Result<UserDto, DataError.Network> =
        httpClient.patch(
            route = userRoute(id),
            body = UpdateUserRequestDto(name = name, email = email)
        )

    /** GoRest's filter also matches partial emails, so the exact match is picked here. */
    override suspend fun findUserByEmail(email: String): Result<UserDto?, DataError.Network> =
        httpClient.get<List<UserDto>>(
            route = ApiConstants.USERS_ROUTE,
            queryParameters = mapOf(ApiConstants.EMAIL_PARAM to email)
        ).map { response -> response.body.firstOrNull { it.email.equals(email, ignoreCase = true) } }

    override suspend fun deleteUser(id: Long): EmptyResult<DataError.Network> = httpClient.delete(route = userRoute(id))

    private suspend fun fetchPage(page: Int): Result<NetworkResponse<List<UserDto>>, DataError.Network> =
        httpClient.get(
            route = ApiConstants.USERS_ROUTE,
            queryParameters = mapOf(
                ApiConstants.PAGE_PARAM to page,
                ApiConstants.PER_PAGE_PARAM to ApiConstants.USERS_PAGE_SIZE
            )
        )

    private fun NetworkResponse<*>.pageCount(): Int? = headers[ApiConstants.PAGE_COUNT_HEADER]?.toIntOrNull()

    private fun userRoute(id: Long) = "${ApiConstants.USERS_ROUTE}/$id"
}
