package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.data.networking.ApiConstants
import com.androidlearning.sliide_challenge.core.data.networking.NetworkResponse
import com.androidlearning.sliide_challenge.core.data.networking.get
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.core.domain.util.map
import io.ktor.client.HttpClient

class KtorMainApi(private val httpClient: HttpClient) : MainApi {

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

    private suspend fun fetchPage(page: Int): Result<NetworkResponse<List<UserDto>>, DataError.Network> =
        httpClient.get(
            route = ApiConstants.USERS_ROUTE,
            queryParameters = mapOf(
                ApiConstants.PAGE_PARAM to page,
                ApiConstants.PER_PAGE_PARAM to ApiConstants.USERS_PAGE_SIZE
            )
        )

    private fun NetworkResponse<*>.pageCount(): Int? = headers[ApiConstants.PAGE_COUNT_HEADER]?.toIntOrNull()
}
