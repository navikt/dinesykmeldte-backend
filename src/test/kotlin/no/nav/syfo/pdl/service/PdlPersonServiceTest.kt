package no.nav.syfo.pdl.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.common.exception.ServiceUnavailableException
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.model.formatName
import no.nav.syfo.util.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo

class PdlPersonServiceTest :
    FunSpec({
        val sykmeldingId = UUID.randomUUID().toString()
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
                    assertFailsWith<ServiceUnavailableException> {
                        pdlPersonService.getPerson(fnr, sykmeldingId)
                    }
                }
            }
            test("Henter navn og aktørid for person som finnes i PDL") {
                httpClient.respond(getTestData())
                runBlocking {
                    val person = pdlPersonService.getPerson(fnr, sykmeldingId)

                    person.navn.formatName() shouldBeEqualTo "Rask Saks"
                }
            }
            test("Henter kommune og bydel og aktørid for person som finnes i PDL") {
                httpClient.respond(getTestData())
                runBlocking {
                    val person = pdlPersonService.getPerson(fnr, sykmeldingId)

                    person.gtKommune shouldBeEqualTo null
                    person.gtBydel shouldBeEqualTo "030102"

                }
            }

            test("Feiler hvis navn mangler i PDL") {
                httpClient.respond(getTestDataUtenNavn())
                assertFailsWith<NameNotFoundInPdlException> {
                    runBlocking { pdlPersonService.getPerson(fnr, sykmeldingId) }
                }
            }
            test("Feiler hvis aktørid mangler i PDL") {
                httpClient.respond(getTestDataUtenAktorId())
                assertFailsWith<RuntimeException> {
                    runBlocking { pdlPersonService.getPerson(fnr, sykmeldingId) }
                }
            }

            test("Feiler hvis PDL returnerer feilmelding") {
                httpClient.respond(getErrorResponse())
                assertFailsWith<NameNotFoundInPdlException> {
                    runBlocking { pdlPersonService.getPerson(fnr, sykmeldingId) }
                }
            }
        }
    })
