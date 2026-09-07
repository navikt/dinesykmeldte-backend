package no.nav.syfo.pdl.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.common.exception.ServiceUnavailableException
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.exceptions.PdlPersonoppslagFailedException
import no.nav.syfo.pdl.model.formatName
import no.nav.syfo.util.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PdlPersonServiceTest :
    FunSpec({
        val fnr = "12345678910"
        val accessTokenClient = mockk<AccessTokenClient>()
        val httpClient = HttpClientTest()

        val pdlClient = PdlClient(httpClient.httpClient, "graphqlend")
        val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, "scope")

        beforeEach { coEvery { accessTokenClient.getAccessToken(any()) } returns "token" }

        context("PdlPersonService") {
            test("Handle error") {
                runBlocking {
                    httpClient.respond {
                        delay(2_000)
                        null
                    }
                    val exception =
                        assertFailsWith<PdlPersonoppslagFailedException> {
                            pdlPersonService.getPerson(fnr)
                        }
                    assertIs<ServiceUnavailableException>(exception.cause)
                }
            }
            test("Henter navn for person som finnes i PDL") {
                httpClient.respond(getTestData())
                runBlocking {
                    val person = pdlPersonService.getPerson(fnr)

                    person.navn.formatName() shouldBeEqualTo "Rask Saks"
                }
            }

            test("Feiler hvis navn mangler i PDL") {
                httpClient.respond(getTestDataUtenNavn())
                assertFailsWith<NameNotFoundInPdlException> {
                    runBlocking { pdlPersonService.getPerson(fnr) }
                }
            }
            test("Bevarer manglende navn når PDL returnerer unauthorized med data") {
                httpClient.respond(getErrorResponse())
                assertFailsWith<NameNotFoundInPdlException> {
                    runBlocking { pdlPersonService.getPerson(fnr) }
                }
            }

            test("Behandler not_found fra PDL som manglende person") {
                httpClient.respond(getErrorResponse().replace("unauthorized", "not_found"))
                assertFailsWith<NameNotFoundInPdlException> {
                    runBlocking { pdlPersonService.getPerson(fnr) }
                }
            }

            listOf(
                "{}",
                """{"data":null}""",
                """{"data":null,"errors":[{"extensions":{"code":"server_error"}}]}""",
            ).forEach { response ->
                test("Respons uten data beholder feilsporet: $response") {
                    httpClient.respond(response)
                    assertFailsWith<PdlPersonoppslagFailedException> {
                        pdlPersonService.getPerson(fnr)
                    }
                }
            }
        }
    })
