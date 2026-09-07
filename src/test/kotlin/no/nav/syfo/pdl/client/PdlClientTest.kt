package no.nav.syfo.pdl.client

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import no.nav.syfo.util.objectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdlClientTest :
    FunSpec({
        test("sender bare feltene tjenesten bruker") {
            val fnr = "12345678910"
            val token = "token"
            lateinit var serializedRequest: String
            val engine =
                MockEngine { request ->
                    serializedRequest = request.body.toByteArray().toString(Charsets.UTF_8)
                    assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
                    assertEquals("SYM", request.headers["TEMA"])
                    assertEquals("B229", request.headers["Behandlingsnummer"])
                    respond(
                        content =
                            """
                            {
                              "data": {
                                "person": {
                                  "navn": [
                                    {"fornavn":"RASK","mellomnavn":null,"etternavn":"SAKS"}
                                  ]
                                }
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )
                }
            val httpClient =
                HttpClient(engine) {
                    install(ContentNegotiation) { jackson { registerKotlinModule() } }
                }
            val pdlClient = PdlClient(httpClient, "https://pdl.test/graphql")

            pdlClient.getPerson(fnr = fnr, token = token)

            val requestJson = objectMapper.readTree(serializedRequest)
            val query = requestJson["query"].asText()
            assertTrue(query.contains("hentPerson"))
            assertFalse(query.contains("hentIdenter"))
            assertEquals(fnr, requestJson["variables"]["ident"].asText())
            httpClient.close()
        }
    })
