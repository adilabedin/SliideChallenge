package com.androidlearning.sliide_challenge.core.data.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide_challenge.core.domain.util.DataError
import com.androidlearning.sliide_challenge.core.domain.util.Result
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.test.Test

class PlatformNetworkErrorAndroidTest {

    private fun clientThrowing(error: Throwable) =
        HttpClientFactory.create(apiKey = "test-token", engine = MockEngine { throw error })

    @Test
    fun `unknown host maps to no internet`() = runTest {
        val result = clientThrowing(UnknownHostException("gorest.co.in")).get<JsonObject>(route = "users")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }

    @Test
    fun `connection refused maps to no internet`() = runTest {
        val result = clientThrowing(ConnectException()).get<JsonObject>(route = "users")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }
}
