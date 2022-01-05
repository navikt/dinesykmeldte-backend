package no.nav.syfo.virksomhet.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.util.addAuthorizationHeader
import no.nav.syfo.util.minifyApiResponse
import no.nav.syfo.util.withKtor
import no.nav.syfo.virksomhet.model.Virksomhet
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VirksomhetApiKtTest : Spek({
    val virksomhetService = mockk<VirksomhetService>()
    val env = mockk<Environment>()

    beforeEachTest {
        every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
    }

    withKtor(env, {
        registerVirksomhetApi(virksomhetService)
    }) {
        describe("Virksomhet API") {
            it("should return empty list") {
                every { virksomhetService.getVirksomheter("08086912345") } returns emptyList()

                with(
                    handleRequest(HttpMethod.Get, "/api/virksomheter") { addAuthorizationHeader() }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.content shouldBeEqualTo "[]"
                }
            }

            it("should return list of virksomheter when found") {
                every { virksomhetService.getVirksomheter("08086912345") } returns listOf(
                    Virksomhet(navn = "Test virksomhet 1", orgnummer = "test-virksomhet-1"),
                    Virksomhet(navn = "Test virksomhet 2", orgnummer = "test-virksomhet-2"),
                )

                with(
                    handleRequest(HttpMethod.Get, "/api/virksomheter") { addAuthorizationHeader() }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.content shouldBeEqualTo """
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
})
