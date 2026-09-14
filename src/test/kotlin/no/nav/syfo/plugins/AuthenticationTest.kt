package no.nav.syfo.plugins

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import kotlinx.coroutines.runBlocking
import no.nav.syfo.util.addAuthorizationHeader
import no.nav.syfo.util.withKtor
import org.amshove.kluent.shouldBeEqualTo

class AuthenticationTest :
    FunSpec(
        {
            listOf("Level4", "idporten-loa-high").forEach { acr ->
                test("should accept high assurance acr $acr") {
                    withKtor({ get("/protected") { call.respond(HttpStatusCode.OK) } }) {
                        runBlocking {
                            client
                                .get("/protected") { addAuthorizationHeader(level = acr) }
                                .status shouldBeEqualTo HttpStatusCode.OK
                        }
                    }
                }
            }

            listOf("Level3", "idporten-loa-substantial", null, "unknown").forEach { acr ->
                test("should reject non-high assurance acr ${acr ?: "missing"}") {
                    withKtor({ get("/protected") { call.respond(HttpStatusCode.OK) } }) {
                        runBlocking {
                            client
                                .get("/protected") { addAuthorizationHeader(level = acr) }
                                .status shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                }
            }
        },
    )
