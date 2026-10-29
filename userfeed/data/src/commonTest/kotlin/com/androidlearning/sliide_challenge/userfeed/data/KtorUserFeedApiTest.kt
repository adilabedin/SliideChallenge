package com.androidlearning.sliide_challenge.userfeed.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.data.networking.HttpClientFactory
import com.androidlearning.sliide_challenge.core.data.user.UserDto
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class KtorUserFeedApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(status: HttpStatusCode, body: String = "") = KtorUserFeedApi(
        HttpClientFactory.create(
            apiKey = "test-token",
            engine = MockEngine { request ->
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
    )

    private val userJson = """{"id":7,"name":"Ada","email":"ada@x.io","gender":"female","status":"active"}"""

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.decodeFromString(JsonObject.serializer(), (body as TextContent).text)

    @Test
    fun `create posts name and email plus the required gender and status`() = runTest {
        val result = api(HttpStatusCode.Created, userJson).createUser("Ada", "ada@x.io")

        assertThat(result).isEqualTo(Result.Success(UserDto(id = 7, name = "Ada", email = "ada@x.io")))
        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Post)
        assertThat(request.url.toString()).isEqualTo("https://gorest.co.in/public/v2/users")
        assertThat(request.jsonBody()).isEqualTo(
            JsonObject(
                mapOf(
                    "name" to JsonPrimitive("Ada"),
                    "email" to JsonPrimitive("ada@x.io"),
                    "gender" to JsonPrimitive(CreateUserRequestDto.DEFAULT_GENDER),
                    "status" to JsonPrimitive(CreateUserRequestDto.DEFAULT_STATUS),
                ),
            ),
        )
    }

    @Test
    fun `create maps 422 to UNPROCESSABLE_ENTITY`() = runTest {
        val result = api(HttpStatusCode.UnprocessableEntity, "[]").createUser("Ada", "taken@x.io")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNPROCESSABLE_ENTITY))
    }

    @Test
    fun `update patches the user route with name and email`() = runTest {
        val result = api(HttpStatusCode.OK, userJson).updateUser(7, "Ada", "ada@x.io")

        assertThat(result).isEqualTo(Result.Success(UserDto(id = 7, name = "Ada", email = "ada@x.io")))
        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Patch)
        assertThat(request.url.toString()).isEqualTo("https://gorest.co.in/public/v2/users/7")
        assertThat(request.jsonBody()).isEqualTo(
            JsonObject(mapOf("name" to JsonPrimitive("Ada"), "email" to JsonPrimitive("ada@x.io"))),
        )
    }

    @Test
    fun `find by email queries users by email and picks the exact match among partial ones`() = runTest {
        val body = """[{"id":3,"name":"Ada","email":"ada@x.io.uk"},{"id":7,"name":"Ada","email":"ADA@x.io"}]"""

        val result = api(HttpStatusCode.OK, body).findUserByEmail("ada@x.io")

        assertThat(result).isEqualTo(Result.Success(UserDto(id = 7, name = "Ada", email = "ADA@x.io")))
        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Get)
        assertThat(request.url.toString()).isEqualTo("https://gorest.co.in/public/v2/users?email=ada%40x.io")
    }

    @Test
    fun `find by email returns null when only partial matches come back`() = runTest {
        val body = """[{"id":3,"name":"Ada","email":"ada@x.io.uk"}]"""

        val result = api(HttpStatusCode.OK, body).findUserByEmail("ada@x.io")

        assertThat(result).isEqualTo(Result.Success(null))
    }

    @Test
    fun `delete succeeds on 204 with an empty body`() = runTest {
        val result = api(HttpStatusCode.NoContent).deleteUser(7)

        assertThat(result).isEqualTo(Result.Success(Unit))
        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Delete)
        assertThat(request.url.toString()).isEqualTo("https://gorest.co.in/public/v2/users/7")
    }

    @Test
    fun `delete maps 404 to NOT_FOUND`() = runTest {
        val result = api(HttpStatusCode.NotFound, """{"message":"Resource not found"}""").deleteUser(7)

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NOT_FOUND))
    }
}
