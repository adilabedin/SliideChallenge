package com.androidlearning.sliide_challenge.core.data.user

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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

    private val lastPageBody = """
        [{"id":8686808,"name":"Rep. Kailash Sinha","email":"kailash_rep_sinha@gorczany.example","gender":"female","status":"inactive"}]
    """.trimIndent()

    private fun api(
        firstPageStatus: HttpStatusCode = HttpStatusCode.OK,
        pagesHeader: String? = "294",
    ) = KtorMainApi(
        HttpClientFactory.create(
            apiKey = "test-token",
            engine = MockEngine { request ->
                requestedUrls += request.url
                val page = request.url.parameters["page"]
                if (page == null) {
                    val headers = buildList {
                        add(HttpHeaders.ContentType to listOf("application/json"))
                        pagesHeader?.let { add("X-Pagination-Pages" to listOf(it)) }
                    }
                    respond("[]", firstPageStatus, headersOf(*headers.toTypedArray()))
                } else {
                    respond(lastPageBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        ),
    )

    @Test
    fun `reads page count header then fetches that page`() = runTest {
        val result = api().getLastPageUsers()

        assertThat(result).isEqualTo(
            Result.Success(
                listOf(
                    UserDto(
                        id = 8686808,
                        name = "Rep. Kailash Sinha",
                        email = "kailash_rep_sinha@gorczany.example",
                        gender = "female",
                        status = "inactive",
                    ),
                ),
            ),
        )
        assertThat(requestedUrls.map { it.toString() }).containsExactly(
            "https://gorest.co.in/public/v2/users",
            "https://gorest.co.in/public/v2/users?page=294",
        )
    }

    @Test
    fun `missing page count header falls back to page 1`() = runTest {
        api(pagesHeader = null).getLastPageUsers()

        assertThat(requestedUrls.last().parameters["page"]).isEqualTo("1")
    }

    @Test
    fun `first page failure is returned without fetching the last page`() = runTest {
        val result = api(firstPageStatus = HttpStatusCode.Unauthorized).getLastPageUsers()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
        assertThat(requestedUrls.size).isEqualTo(1)
        assertThat(requestedUrls.single().parameters["page"]).isNull()
    }
}
