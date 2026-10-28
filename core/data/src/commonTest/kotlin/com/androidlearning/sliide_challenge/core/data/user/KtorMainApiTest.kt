package com.androidlearning.sliide_challenge.core.data.user

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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KtorMainApiTest {

    private val requestedUrls = mutableListOf<Url>()

    private fun usersJson(id: Long) =
        """[{"id":$id,"name":"User $id","email":"user$id@example.test","gender":"female","status":"active"}]"""

    /**
     * Serves `/users?page=N`. [pageCountForRequest] is the `x-pagination-pages` value reported on each
     * request (by request index), so tests can simulate the dataset shrinking between calls. Pages past
     * the reported count come back empty, as GoRest does.
     */
    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        pageCountForRequest: (Int) -> String? = { "5" },
    ) = KtorMainApi(
        HttpClientFactory.create(
            apiKey = "test-token",
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
                respond(body, status, headersOf(*headers.toTypedArray()))
            },
        ),
    )

    private fun requestedPages() = requestedUrls.map { it.parameters["page"] }

    @Test
    fun `reads the page count from page 1 then fetches the last page`() = runTest {
        val result = api().getLastPageUsers()

        assertThat(result).isEqualTo(
            Result.Success(listOf(UserDto(id = 5, name = "User 5", email = "user5@example.test"))),
        )
        assertThat(requestedUrls.map { it.toString() }).containsExactly(
            "https://gorest.co.in/public/v2/users?page=1&per_page=20",
            "https://gorest.co.in/public/v2/users?page=5&per_page=20",
        )
    }

    @Test
    fun `a single page needs only one request`() = runTest {
        val result = api(pageCountForRequest = { "1" }).getLastPageUsers()

        assertThat(result).isEqualTo(
            Result.Success(listOf(UserDto(id = 1, name = "User 1", email = "user1@example.test"))),
        )
        assertThat(requestedPages()).containsExactly("1")
    }

    @Test
    fun `retries the new last page when the dataset shrinks between requests`() = runTest {
        val result = api(pageCountForRequest = { index -> if (index == 0) "5" else "4" }).getLastPageUsers()

        assertThat(result).isEqualTo(
            Result.Success(listOf(UserDto(id = 4, name = "User 4", email = "user4@example.test"))),
        )
        assertThat(requestedPages()).containsExactly("1", "5", "4")
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
