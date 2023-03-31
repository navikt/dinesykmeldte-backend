package no.nav.syfo.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import no.nav.syfo.common.exception.ServiceUnavailableException
import no.nav.syfo.log
import java.net.ServerSocket

data class ResponseData(
    val httpStatusCode: HttpStatusCode,
    val content: String,
    val headers: Headers = headersOf("Content-Type", listOf("application/json")),
)

class HttpClientTest {

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        routing {
            get("*") {
                response()
            }
            post("*") {
                response()
            }
        }
    }.start(false)

    private suspend fun PipelineContext<Unit, ApplicationCall>.response() {
        when (val response = responseFunction.invoke()) {
            null -> call.respond(HttpStatusCode.OK)
            else -> {
                call.respondText(response.content, ContentType.Application.Json, response.httpStatusCode)
                call.respond(response.httpStatusCode, response.content)
            }
        }
    }

    var responseFunction: suspend () -> ResponseData? = {
        responseData
    }

    var responseData: ResponseData? = null

    fun respond(status: HttpStatusCode, content: String = "") {
        responseData = ResponseData(status, content, headersOf())
    }

    fun respond(function: suspend () -> ResponseData?) {
        responseFunction = function
    }

    fun respond(data: String) {
        responseFunction = { ResponseData(HttpStatusCode.OK, data) }
    }

    val httpClient = HttpClient(Apache) {
        defaultRequest {
            host = "localhost"
            port = mockHttpServerPort
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 1L
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn("Retrying for statuscode ${response.status.value}, for url ${request.url}")
                    true
                } else {
                    false
                }
            }
        }
    }
}
