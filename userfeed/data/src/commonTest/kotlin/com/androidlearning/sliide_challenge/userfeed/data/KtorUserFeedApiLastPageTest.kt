package com.androidlearning.sliide_challenge.userfeed.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.data.networking.HttpClientFactory
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class KtorUserFeedApiLastPageTest {

    private val requestedUrls = mutableListOf<Url>()

    private fun usersJson(id: Long) =
        """[{"id":$id,"name":"User $id","email":"user$id@example.test","gender":"female","status":"active"}]"""

    /**
     * Serves `/users?page=N`. [statusForRequest] and [pageCountForRequest] give the status and the
     * `x-pagination-pages` value for each request (by request index). Pages past the reported count come
     * back empty, as GoRest does.
     */
    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        statusForRequest: (Int) -> HttpStatusCode = { status },
        pageCountForRequest: (Int) -> String? = { "5" }
    ) = KtorUserFeedApi(
        HttpClientFactory.create(
            apiKey = "test-token",
            json = Json { ignoreUnknownKeys = true },
            enableLogging = false,
            engine = MockEngine { request ->
                val requestIndex = requestedUrls.size
                requestedUrls += request.url
                val page = request.url.parameters["page"]!!.toLong()
                val pageCount = pageCountForRequest(requestIndex)
                val body = if (pageCount != null && page > pageCount.toLong()) "[]" else usersJson(id = page)
                val headers = buildList {
                    add(HttpHeaders.ContentType to listOf("application/json"))
                    if (pageCount != null) add("x-pagination-pages" to listOf(pageCount))
                }
                respond(body, statusForRequest(requestIndex), headersOf(*headers.toTypedArray()))
            }
        )
    )

    private fun requestedPages() = requestedUrls.map { it.parameters["page"] }

    @Test
    fun `reads the page count from page 1 then fetches the last page`() = runTest {
        val result = api().getLastPageUsers()

        assertThat(result).isEqualTo(
            Result.Success(listOf(UserDto(id = 5, name = "User 5", email = "user5@example.test")))
        )
        assertThat(requestedUrls.map { it.toString() }).containsExactly(
            "https://gorest.co.in/public/v2/users?page=1&per_page=20",
            "https://gorest.co.in/public/v2/users?page=5&per_page=20"
        )
    }

    @Test
    fun `a single page needs only one request`() = runTest {
        val result = api(pageCountForRequest = { "1" }).getLastPageUsers()

        assertThat(result).isEqualTo(
            Result.Success(listOf(UserDto(id = 1, name = "User 1", email = "user1@example.test")))
        )
        assertThat(requestedPages()).containsExactly("1")
    }

    @Test
    fun `an empty dataset is an empty last page`() = runTest {
        val result = api(pageCountForRequest = { "0" }).getLastPageUsers()

        assertThat(result).isEqualTo(Result.Success(emptyList()))
        assertThat(requestedPages()).containsExactly("1")
    }

    @Test
    fun `a failure on the second request fails the whole call`() = runTest {
        val failSecondRequest = { index: Int ->
            if (index == 0) HttpStatusCode.OK else HttpStatusCode.InternalServerError
        }

        val result = api(statusForRequest = failSecondRequest).getLastPageUsers()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
        assertThat(requestedPages()).containsExactly("1", "5")
    }

    @Test
    fun `missing page count header is an error rather than a guess`() = runTest {
        val result = api(pageCountForRequest = { null }).getLastPageUsers()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNKNOWN))
        assertThat(requestedPages()).containsExactly("1")
    }

    @Test
    fun `http failure is mapped to a network error`() = runTest {
        val result = api(status = HttpStatusCode.Unauthorized).getLastPageUsers()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
    }
}
