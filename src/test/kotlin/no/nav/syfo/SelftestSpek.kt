package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.plugins.configureNaisResources
import org.amshove.kluent.shouldBeEqualTo

class SelftestSpek :
    FunSpec(
        {
            context("Successfull liveness and readyness tests") {
                test("Returns ok on is_alive") {
                    testApplication {
                        application {
                            val applicationState = ApplicationState()
                            applicationState.ready = true
                            applicationState.alive = true
                            configureNaisResources(applicationState)
                        }
                        val response = client.get("/internal/is_alive")
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        response.bodyAsText() shouldBeEqualTo "I'm alive! :)"
                    }
                }
                test("Returns ok in is_ready") {
                    testApplication {
                        application {
                            val applicationState = ApplicationState()
                            applicationState.ready = true
                            applicationState.alive = true
                            configureNaisResources(applicationState)
                        }
                        val response = client.get("/internal/is_ready")
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        response.bodyAsText() shouldBeEqualTo "I'm ready! :)"
                    }
                }
            }
            context("Unsuccessful liveness and readyness") {
                test("Returns internal server error when liveness check fails") {
                    testApplication {
                        application {
                            val applicationState = ApplicationState()
                            applicationState.ready = false
                            applicationState.alive = false
                            configureNaisResources(applicationState)
                        }
                        val response = client.get("/internal/is_alive")

                        response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                        response.bodyAsText() shouldBeEqualTo "I'm dead x_x"
                    }
                }

                test("Returns internal server error when readyness check fails") {
                    testApplication {
                        application {
                            val applicationState = ApplicationState()
                            applicationState.ready = false
                            applicationState.alive = false
                            configureNaisResources(applicationState)
                        }
                        val response = client.get("/internal/is_ready")

                        response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                        response.bodyAsText() shouldBeEqualTo "Please wait! I'm not ready :("
                    }
                }
            }
        },
    )
