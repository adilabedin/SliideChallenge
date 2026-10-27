package com.androidlearning.sliide_challenge.core.data.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val GOREST_BASE_URL = "https://gorest.co.in/public/v2/"

object HttpClientFactory {

    /** Uses the platform engine on the classpath (OkHttp on Android, Darwin on iOS). */
    fun create(): HttpClient = HttpClient { configure() }

    fun create(engine: HttpClientEngine): HttpClient = HttpClient(engine) { configure() }

    private fun HttpClientConfig<*>.configure() {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
        }
        defaultRequest {
            url(GOREST_BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }
}
