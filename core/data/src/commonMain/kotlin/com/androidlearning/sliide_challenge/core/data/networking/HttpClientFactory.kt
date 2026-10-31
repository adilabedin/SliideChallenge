package com.androidlearning.sliide_challenge.core.data.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.Platform
import kotlinx.serialization.json.Json

object HttpClientFactory {

    /** Uses the platform engine on the classpath (OkHttp on Android, Darwin on iOS). */
    fun create(apiKey: String, json: Json, enableLogging: Boolean): HttpClient =
        HttpClient { configure(apiKey, json, enableLogging) }

    fun create(apiKey: String, json: Json, enableLogging: Boolean, engine: HttpClientEngine): HttpClient =
        HttpClient(engine) { configure(apiKey, json, enableLogging) }

    private fun HttpClientConfig<*>.configure(apiKey: String, json: Json, enableLogging: Boolean) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = ApiConstants.TIMEOUT_MILLIS
            connectTimeoutMillis = ApiConstants.TIMEOUT_MILLIS
            socketTimeoutMillis = ApiConstants.TIMEOUT_MILLIS
        }
        if (enableLogging) {
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.INFO
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
        }
        install(DefaultRequest.Plugin) {
            url(ApiConstants.BASE_URL)
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, bearerToken(apiKey))
            }
        }
    }

    private fun bearerToken(rawToken: String): String =
        if (rawToken.startsWith("Bearer ", ignoreCase = true)) rawToken else "Bearer $rawToken"
}
