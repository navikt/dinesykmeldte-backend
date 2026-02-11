package no.nav.syfo.virksomhet.api

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.util.addAuthorizationHeader
import no.nav.syfo.util.minifyApiResponse
import no.nav.syfo.util.withKtor
import no.nav.syfo.virksomhet.model.Virksomhet
import org.amshove.kluent.shouldBeEqualTo

object VirksomhetApiKtTest :
    FunSpec(
        {
            val virksomhetService = mockk<VirksomhetService>()
            val env = mockk<Environment>()

            beforeEach {
                every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
            }

            context("Virksomhet API") {
                test("should return empty list") {
                    withKtor({ registerVirksomhetApi(virksomhetService) }) {
                        coEvery { virksomhetService.getVirksomheter("08086912345") } returns
                            emptyList()

                        runBlocking {
                            val response =
                                client.get("/api/virksomheter") { addAuthorizationHeader() }

                            response.status shouldBeEqualTo HttpStatusCode.OK
                            response.bodyAsText() shouldBeEqualTo "[]"
                        }
                    }
                }

                test("should return list of virksomheter when found") {
                    withKtor({ registerVirksomhetApi(virksomhetService) }) {
                        coEvery { virksomhetService.getVirksomheter("08086912345") } returns
                            listOf(
                                Virksomhet(
                                    navn = "Test virksomhet 1",
                                    orgnummer = "test-virksomhet-1",
                                ),
                                Virksomhet(
                                    navn = "Test virksomhet 2",
                                    orgnummer = "test-virksomhet-2",
                                ),
                            )
                        runBlocking {
                            val response =
                                client.get("/api/virksomheter") { addAuthorizationHeader() }
                            response.status shouldBeEqualTo HttpStatusCode.OK
                            response.bodyAsText() shouldBeEqualTo
                                """
                        [
                          {
                            "navn": "Test virksomhet 1",
                            "orgnummer": "test-virksomhet-1"
                          },
                          {
                            "navn": "Test virksomhet 2",
                            "orgnummer": "test-virksomhet-2"
                          }
                        ]
                        """.minifyApiResponse()
                        }
                    }
                }
            }
        },
    )
