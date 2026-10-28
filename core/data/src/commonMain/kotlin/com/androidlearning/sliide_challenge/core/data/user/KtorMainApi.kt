package com.androidlearning.sliide_challenge.core.data.user

import com.androidlearning.sliide_challenge.core.data.networking.ApiConstants
import com.androidlearning.sliide_challenge.core.data.networking.NetworkResponse
import com.androidlearning.sliide_challenge.core.data.networking.getWithHeaders
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import io.ktor.client.HttpClient

class KtorMainApi(
    private val httpClient: HttpClient,
) : MainApi {

    /**
     * GoRest only reports the page count in a header, so this takes two requests: page 1 to read
     * `x-pagination-pages`, then that page. The dataset is shared and changes constantly, so if it
     * shrinks in between, the requested page comes back empty with the new count; we retry once.
     */
    override suspend fun getLastPageUsers(): Result<List<UserDto>, DataError.Network> {
        val firstPage = when (val result = fetchPage(1)) {
            is Result.Error -> return result
            is Result.Success -> result.data
        }
        val lastPageNumber = firstPage.pageCount() ?: return Result.Error(DataError.Network.UNKNOWN)
        if (lastPageNumber <= 1) return Result.Success(firstPage.body)

        val lastPage = when (val result = fetchPage(lastPageNumber)) {
            is Result.Error -> return result
            is Result.Success -> result.data
        }
        val currentPageCount = lastPage.pageCount()
        if (lastPage.body.isEmpty() && currentPageCount != null && currentPageCount in 1 until lastPageNumber) {
            return when (val retry = fetchPage(currentPageCount)) {
                is Result.Error -> retry
                is Result.Success -> Result.Success(retry.data.body)
            }
        }
        return Result.Success(lastPage.body)
    }

    private suspend fun fetchPage(page: Int): Result<NetworkResponse<List<UserDto>>, DataError.Network> =
        httpClient.getWithHeaders(
            route = ApiConstants.USERS_ROUTE,
            queryParameters = mapOf(
                ApiConstants.PAGE_PARAM to page,
                ApiConstants.PER_PAGE_PARAM to ApiConstants.USERS_PAGE_SIZE,
            ),
        )

    private fun NetworkResponse<*>.pageCount(): Int? =
        headers[ApiConstants.PAGE_COUNT_HEADER]?.toIntOrNull()
}
