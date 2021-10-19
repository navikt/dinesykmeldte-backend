package no.nav.syfo.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import no.nav.syfo.objectMapper

data class ResponseData(val httpStatusCode: HttpStatusCode, val content: String, val headers: Headers = headersOf("Content-Type", listOf("application/json")))

class HttpClientTest {

    var responseData: ResponseData? = null

    fun respond(status: HttpStatusCode, content: String = "") {
        responseData = ResponseData(status, content, headersOf())
    }

    fun respond(data: Any) {
        responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(data))
    }
    fun respond(data: String) {
        responseData = ResponseData(HttpStatusCode.OK, data)
    }
    val httpClient = HttpClient(MockEngine) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        engine {
            addHandler { request ->
                respond(responseData!!.content, responseData!!.httpStatusCode, responseData!!.headers)
            }
        }
    }
}
