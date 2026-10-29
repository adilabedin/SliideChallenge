package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.data.networking.ApiConstants
import com.androidlearning.sliide_challenge.core.data.networking.NetworkResponse
import com.androidlearning.sliide_challenge.core.data.networking.get
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import io.ktor.client.HttpClient

class KtorMainApi(
    private val httpClient: HttpClient,
) : MainApi {

    /**
     * Where we expect the last page to be, learned from the previous response. Only a hint: a stale
     * value costs one extra request, never a wrong answer, so unsynchronised access is fine.
     */
    private var lastPageHint = 1

    /**
     * GoRest has no "last page" link or reverse sort; the page count only arrives in the
     * `x-pagination-pages` header of each response. So we request the page we believe is last and
     * check its header: if it agrees, that's the answer; if not (cold start, or the shared dataset grew
     * or shrank), we follow the reported count. Cold start takes two requests, later refreshes one.
     */
    override suspend fun getLastPageUsers(): Result<List<UserDto>, DataError.Network> {
        var page = lastPageHint
        repeat(MAX_ATTEMPTS) {
            val response = when (val result = fetchPage(page)) {
                is Result.Error -> return result
                is Result.Success -> result.data
            }
            // An empty dataset reports 0 pages; page 1 is then the (empty) last page.
            val lastPage = response.pageCount()?.coerceAtLeast(1)
                ?: return Result.Error(DataError.Network.UNKNOWN)
            lastPageHint = lastPage
            if (lastPage == page) return Result.Success(response.body)
            page = lastPage
        }
        // The dataset moved on every request; surface a failure and keep showing the cache.
        return Result.Error(DataError.Network.UNKNOWN)
    }

    private suspend fun fetchPage(page: Int): Result<NetworkResponse<List<UserDto>>, DataError.Network> =
        httpClient.get(
            route = ApiConstants.USERS_ROUTE,
            queryParameters = mapOf(
                ApiConstants.PAGE_PARAM to page,
                ApiConstants.PER_PAGE_PARAM to ApiConstants.USERS_PAGE_SIZE,
            ),
        )

    private fun NetworkResponse<*>.pageCount(): Int? =
        headers[ApiConstants.PAGE_COUNT_HEADER]?.toIntOrNull()

    private companion object {
        /** Cold start needs two; one more absorbs the dataset changing in between. */
        const val MAX_ATTEMPTS = 3
    }
}
