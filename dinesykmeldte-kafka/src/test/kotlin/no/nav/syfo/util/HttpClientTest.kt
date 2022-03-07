package no.nav.syfo.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.host
import io.ktor.client.request.port
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.jackson.jackson
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.common.exception.ServiceUnavailableException
import java.net.ServerSocket

data class ResponseData(
    val httpStatusCode: HttpStatusCode,
    val content: String,
    val headers: Headers = headersOf("Content-Type", listOf("application/json"))
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
                when (val response = responseFunction.invoke()) {
                    null -> call.respond(HttpStatusCode.OK)
                    else -> {
                        call.respondText(response.content, ContentType.Application.Json, response.httpStatusCode)
                        call.respond(response.httpStatusCode, response.content)
                    }
                }
            }
        }
    }.start(false)

    var responseFunction: suspend () -> ResponseData? = {
        responseData
    }

    var responseData: ResponseData? = null

    fun respond(status: HttpStatusCode, content: String = "") {
        responseData = ResponseData(status, content, headersOf())
    }

    suspend fun respond(function: suspend () -> ResponseData?) {
        responseFunction = function
    }

    fun respond(data: String) {
        responseFunction = { ResponseData(HttpStatusCode.OK, data) }
    }

    val httpClient = HttpClient(Apache) {
        defaultRequest {
            method = HttpMethod.Get
            host = "localhost"
            port = mockHttpServerPort
        }
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 1L
        }
    }
}
