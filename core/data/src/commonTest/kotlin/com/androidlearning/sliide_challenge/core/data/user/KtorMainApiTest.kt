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

    /** Includes fields we don't model (gender, status) to prove they are ignored. */
    private val latestPageBody = """
        [{"id":8686808,"name":"Rep. Kailash Sinha","email":"kailash_rep_sinha@gorczany.example","gender":"female","status":"inactive"}]
    """.trimIndent()

    private fun api(status: HttpStatusCode = HttpStatusCode.OK) = KtorMainApi(
        HttpClientFactory.create(
            apiKey = "test-token",
            engine = MockEngine { request ->
                requestedUrls += request.url
                respond(latestPageBody, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
    )

    @Test
    fun `fetches the first page in a single request`() = runTest {
        val result = api().getLatestUsers()

        assertThat(result).isEqualTo(
            Result.Success(
                listOf(
                    UserDto(
                        id = 8686808,
                        name = "Rep. Kailash Sinha",
                        email = "kailash_rep_sinha@gorczany.example",
                    ),
                ),
            ),
        )
        assertThat(requestedUrls.map { it.toString() }).containsExactly(
            "https://gorest.co.in/public/v2/users",
        )
    }

    @Test
    fun `http failure is mapped to a network error`() = runTest {
        val result = api(status = HttpStatusCode.Unauthorized).getLatestUsers()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
    }
}
