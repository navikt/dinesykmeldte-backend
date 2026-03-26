package no.nav.syfo.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.engine.apache5.Apache5EngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson

fun httpClientDefault(): HttpClient {
    val config: HttpClientConfig<Apache5EngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 40_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnExceptionIf(maxRetries = 3) { _, cause ->
                cause.isRetryableException()
            }
            exponentialDelay()
            modifyRequest { request ->
                val reason = response?.status ?: cause?.message ?: "unknown"
                logger("HttpRequestRetry")
                    .warn("Retry attempt $retryCount for ${request.url}: $reason")
            }
        }
    }
    return HttpClient(Apache5, config)
}

private fun Throwable.isRetryableException(): Boolean =
    this is java.net.SocketTimeoutException ||
        this is java.net.ConnectException ||
        cause?.isRetryableException() == true
