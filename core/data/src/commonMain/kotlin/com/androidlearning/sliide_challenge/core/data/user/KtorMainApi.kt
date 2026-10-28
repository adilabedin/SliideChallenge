package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.data.networking.constructRoute
import com.androidlearning.sliide_challenge.core.data.networking.get
import com.androidlearning.sliide_challenge.core.data.networking.safeCall
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import com.androidlearning.sliide_challenge.core.domain.util.map
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse

class KtorMainApi(
    private val httpClient: HttpClient,
) : MainApi {

    /**
     * GoRest has no "last page" shortcut and rejects HEAD, so we read the page count from the
     * first page's pagination header and then request that page.
     */
    override suspend fun getLastPageUsers(): Result<List<UserDto>, DataError.Network> {
        val lastPage = when (val pageCount = fetchPageCount()) {
            is Result.Error -> return pageCount
            is Result.Success -> pageCount.data
        }
        return httpClient.get<List<UserDto>>(
            route = USERS_ROUTE,
            queryParameters = mapOf(PAGE_PARAM to lastPage),
        )
    }

    private suspend fun fetchPageCount(): Result<Int, DataError.Network> {
        return safeCall<HttpResponse> {
            httpClient.get { url(constructRoute(USERS_ROUTE)) }
        }.map { response ->
            response.headers[PAGINATION_PAGES_HEADER]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        }
    }

    private companion object {
        const val USERS_ROUTE = "users"
        const val PAGE_PARAM = "page"
        const val PAGINATION_PAGES_HEADER = "X-Pagination-Pages"
    }
}
