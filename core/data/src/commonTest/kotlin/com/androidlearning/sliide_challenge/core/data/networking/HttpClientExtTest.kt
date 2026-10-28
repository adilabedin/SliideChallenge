package com.androidlearning.sliide_challenge.core.data.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class HttpClientExtTest {

    private var lastRequest: HttpRequestData? = null
    private var lastRequestBody: String? = null

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClientFactory.create(
        apiKey = "test-token",
        engine = MockEngine { request ->
            lastRequest = request
            lastRequestBody = request.body.toByteArray().decodeToString()
            handler(request)
        },
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `get success decodes body and sends query params and auth header`() = runTest {
        val client = client { json("""[{"id":1,"name":"Ada"}]""") }

        val result = client.get<JsonArray>(route = "/users", queryParameters = mapOf("page" to 3))

        assertThat(result).isInstanceOf<Result.Success<JsonArray>>()
        val users = (result as Result.Success).data
        assertThat(users.size).isEqualTo(1)
        val request = lastRequest!!
        assertThat(request.method).isEqualTo(HttpMethod.Get)
        assertThat(request.url.toString()).isEqualTo("https://gorest.co.in/public/v2/users?page=3")
        assertThat(request.headers[HttpHeaders.Authorization]).isEqualTo("Bearer test-token")
    }

    @Test
    fun `post serializes body and returns created entity on 201`() = runTest {
        val client = client { json("""{"id":42,"name":"Ada"}""", HttpStatusCode.Created) }
        val body = JsonObject(mapOf("name" to JsonPrimitive("Ada")))

        val result = client.post<JsonObject, JsonObject>(route = "users", body = body)

        assertThat(result).isInstanceOf<Result.Success<JsonObject>>()
        val created = (result as Result.Success).data
        assertThat(created["id"]?.jsonPrimitive?.content).isEqualTo("42")
        assertThat(lastRequest!!.method).isEqualTo(HttpMethod.Post)
        assertThat(lastRequestBody).isEqualTo("""{"name":"Ada"}""")
    }

    @Test
    fun `delete with 204 no content succeeds as Unit`() = runTest {
        val client = client { respond("", HttpStatusCode.NoContent) }

        val result = client.delete<Unit>(route = "users/7")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(lastRequest!!.method).isEqualTo(HttpMethod.Delete)
        assertThat(lastRequest!!.url.toString()).isEqualTo("https://gorest.co.in/public/v2/users/7")
    }

    @Test
    fun `http error statuses map to typed network errors`() = runTest {
        val expectations = mapOf(
            400 to DataError.Network.BAD_REQUEST,
            401 to DataError.Network.UNAUTHORIZED,
            403 to DataError.Network.FORBIDDEN,
            404 to DataError.Network.NOT_FOUND,
            408 to DataError.Network.REQUEST_TIMEOUT,
            409 to DataError.Network.CONFLICT,
            413 to DataError.Network.PAYLOAD_TOO_LARGE,
            422 to DataError.Network.UNPROCESSABLE_ENTITY,
            429 to DataError.Network.TOO_MANY_REQUESTS,
            500 to DataError.Network.SERVER_ERROR,
            503 to DataError.Network.SERVICE_UNAVAILABLE,
            302 to DataError.Network.UNKNOWN,
        )
        expectations.forEach { (status, expected) ->
            val client = client { json("{}", HttpStatusCode.fromValue(status)) }

            val result = client.get<JsonObject>(route = "users")

            assertThat(result, name = "status $status").isEqualTo(Result.Error(expected))
        }
    }

    @Test
    fun `malformed json body maps to serialization error`() = runTest {
        val client = client { json("""{"id": """) }

        val result = client.get<JsonObject>(route = "users/1")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERIALIZATION))
    }

    @Test
    fun `unexpected exception from engine maps to unknown`() = runTest {
        val client = client { throw IllegalStateException("boom") }

        val result = client.get<JsonObject>(route = "users")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNKNOWN))
    }

    @Test
    fun `constructRoute resolves relative and absolute routes`() {
        assertThat(constructRoute("users")).isEqualTo("https://gorest.co.in/public/v2/users")
        assertThat(constructRoute("/users")).isEqualTo("https://gorest.co.in/public/v2/users")
        assertThat(constructRoute("https://example.com/x")).isEqualTo("https://example.com/x")
    }

    @Test
    fun `non network throwable is not mapped by platform mapper`() {
        assertThat(IllegalStateException().toPlatformNetworkError()).isNull()
    }
}
