package no.nav.syfo.sykmelding.pdl.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.sykmelding.pdl.client.PdlClient
import no.nav.syfo.sykmelding.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.sykmelding.pdl.model.toFormattedNameString
import no.nav.syfo.util.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File
import java.util.UUID
import kotlin.test.assertFailsWith

class PdlPersonServiceTest : Spek({
    val sykmeldingId = UUID.randomUUID().toString()
    val fnr = "12345678910"
    val accessTokenClient = mockk<AccessTokenClient>()
    val httpClient = HttpClientTest()

    val graphQlQuery = File("src/main/resources/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    val pdlClient = PdlClient(httpClient.httpClient, "graphqlend", graphQlQuery)
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, "scope")

    beforeEachTest {
        coEvery { accessTokenClient.getAccessToken(any()) } returns "token"
    }

    describe("PdlPersonService") {
        it("Henter navn og aktørid for person som finnes i PDL") {
            httpClient.respond(getTestData())
            runBlocking {
                val person = pdlPersonService.getPerson(fnr, sykmeldingId)

                person.aktorId shouldBeEqualTo "99999999999"
                person.navn.toFormattedNameString() shouldBeEqualTo "Rask Saks"
            }
        }
        it("Feiler hvis navn mangler i PDL") {
            httpClient.respond(getTestDataUtenNavn())
            assertFailsWith<NameNotFoundInPdlException> {
                runBlocking {
                    pdlPersonService.getPerson(fnr, sykmeldingId)
                }
            }
        }
        it("Feiler hvis aktørid mangler i PDL") {
            httpClient.respond(getTestDataUtenAktorId())
            assertFailsWith<RuntimeException> {
                runBlocking {
                    pdlPersonService.getPerson(fnr, sykmeldingId)
                }
            }
        }
        it("Feiler hvis PDL returnerer feilmelding") {
            httpClient.respond(getErrorResponse())
            assertFailsWith<NameNotFoundInPdlException> {
                runBlocking {
                    pdlPersonService.getPerson(fnr, sykmeldingId)
                }
            }
        }
    }
})
