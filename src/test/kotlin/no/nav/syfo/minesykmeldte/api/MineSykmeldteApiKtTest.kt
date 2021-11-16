package no.nav.syfo.minesykmeldte.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.setupAuth
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.model.Arbeidsgiver
import no.nav.syfo.minesykmeldte.model.Behandler
import no.nav.syfo.minesykmeldte.model.Periode
import no.nav.syfo.minesykmeldte.model.PreviewSykmeldt
import no.nav.syfo.minesykmeldte.model.Sykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.testutils.generateJWTLoginservice
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths
import java.time.LocalDate
import java.util.UUID

object MineSykmeldteApiKtTest : Spek({
    val mineSykmeldteService = mockk<MineSykmeldteService>()
    val env = mockk<Environment>()

    beforeEachTest {
        every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
    }

    afterEachTest {
        clearMocks(mineSykmeldteService, env)
    }

    with(TestApplicationEngine()) {
        start()
        val applicationState = ApplicationState()
        applicationState.ready = true
        applicationState.alive = true
        application.install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        application.setupAuth(
            jwkProviderTokenX = JwkProviderBuilder(Paths.get("src/test/resources/jwkset.json").toUri().toURL()).build(),
            tokenXIssuer = "https://sts.issuer.net/myid",
            env = env
        )
        application.routing {
            authenticate("tokenx") {
                registerMineSykmeldteApi(mineSykmeldteService)
            }
        }

        describe("MineSykmeldteApi") {
            it("should get empty list") {
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") { addAuthorizationHeader() }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.content shouldBeEqualTo "[]"
                }
            }

            it("should get data in list") {
                val startdato = LocalDate.now().minusDays(14)
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns listOf(
                    PreviewSykmeldt(
                        narmestelederId = "08086912345",
                        orgnummer = "orgnummer",
                        fnr = "fnr",
                        navn = "navn",
                        startdatoSykefravar = LocalDate.now().minusDays(14),
                        friskmeldt = false,
                        previewSykmeldinger = emptyList(),
                        previewSoknader = emptyList(),
                    )
                )
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") { addAuthorizationHeader() }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.content shouldBeEqualTo """[ {
                          "narmestelederId": "08086912345",
                          "orgnummer": "orgnummer",
                          "fnr": "fnr",
                          "navn": "navn",
                          "startdatoSykefravar": "$startdato",
                          "friskmeldt": false,
                          "previewSykmeldinger": [],
                          "previewSoknader": []
                        }
                    ]""".minifyApiResponse()
                }
            }

            it("should return 401 when missing a valid bearer token") {
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addHeader(
                            "Authorization",
                            "Bearer tull"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("should return 401 when providing the wrong audience") {
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addAuthorizationHeader(audience = "wrong-dummy-client-id")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("should return 401 when providing the wrong issuer") {
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addAuthorizationHeader(issuer = "https://wrong.issuer.net/myid")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("should return 401 when jwt has the wrong access level") {
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addAuthorizationHeader(
                            level = "Level3",
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }

        describe("Min sykmeldt api") {
            it("should respond with bad request if missing sykmeldingId is missing") {
                with(
                    handleRequest(HttpMethod.Get, "/api/sykmelding/not-a-uuid") {
                        addAuthorizationHeader()
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    response.content shouldBeEqualTo """{ "message": "Sykmelding ID is not a valid UUID" }""".minifyApiResponse()
                }
            }

            it("should respond with 404 Not Found if not found in the database ") {
                every {
                    mineSykmeldteService.getSykmelding(
                        UUID.fromString("7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657"),
                        any()
                    )
                } returns null
                with(
                    handleRequest(HttpMethod.Get, "/api/sykmelding/7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657") {
                        addAuthorizationHeader()
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                    response.content shouldBeEqualTo """{ "message": "Sykmeldingen finnes ikke" }""".minifyApiResponse()
                }
                verify(exactly = 1) { mineSykmeldteService.getSykmelding(any(), any()) }
            }

            it("should respond with 404 Not Found if not found in the database ") {
                every {
                    mineSykmeldteService.getSykmelding(
                        UUID.fromString("7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657"),
                        any()
                    )
                } returns createSykmeldingTestData(
                    sykmeldingId = UUID.fromString("63929834-c7e5-4ce5-8742-ee2ff795bfcb"),
                    startdatoSykefravar = LocalDate.parse("2021-01-01"),
                    kontaktDato = LocalDate.parse("2021-01-01"),
                )
                with(
                    handleRequest(HttpMethod.Get, "/api/sykmelding/7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657") {
                        addAuthorizationHeader()
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.content shouldBeEqualTo """
                        {
                          "sykmeldingId": "63929834-c7e5-4ce5-8742-ee2ff795bfcb",
                          "startdatoSykefravar": "2021-01-01",
                          "kontaktDato": "2021-01-01",
                          "navn": "navn",
                          "fnr": "fnr",
                          "lest": false,
                          "arbeidsgiver": {
                            "navn": "Arbeid G. Iversen",
                            "orgnummer": "981298129812",
                            "yrke": "Snekker"
                          },
                          "perioder": [],
                          "arbeidsforEtterPeriode": false,
                          "hensynArbeidsplassen": "hensynArbeidsplassen",
                          "tiltakArbeidsplassen": "tiltakArbeidsplassen",
                          "innspillArbeidsplassen": "innspillArbeidsplassen",
                          "behandler": {
                            "navn": "Beh. Handler",
                            "hprNummer": "80802721231",
                            "telefon": "81549300"
                          }
                        }
                    """.minifyApiResponse()
                }
                verify(exactly = 1) { mineSykmeldteService.getSykmelding(any(), any()) }
            }
        }
    }
})

fun createSykmeldingTestData(
    sykmeldingId: UUID = UUID.randomUUID(),
    startdatoSykefravar: LocalDate = LocalDate.now(),
    kontaktDato: LocalDate = LocalDate.now(),
    navn: String = "navn",
    fnr: String = "fnr",
    lest: Boolean = false,
    arbeidsgiver: Arbeidsgiver = Arbeidsgiver(
        navn = "Arbeid G. Iversen",
        orgnummer = "981298129812",
        yrke = "Snekker",
    ),
    perioder: List<Periode> = emptyList(),
    arbeidsforEtterPeriode: Boolean = false,
    hensynArbeidsplassen: String = "hensynArbeidsplassen",
    tiltakArbeidsplassen: String = "tiltakArbeidsplassen",
    innspillArbeidsplassen: String = "innspillArbeidsplassen",
    behandler: Behandler = Behandler(
        navn = "Beh. Handler",
        hprNummer = "80802721231",
        telefon = "81549300",
    )
): Sykmelding = Sykmelding(
    sykmeldingId = sykmeldingId,
    startdatoSykefravar = startdatoSykefravar,
    kontaktDato = kontaktDato,
    navn = navn,
    fnr = fnr,
    lest = lest,
    arbeidsgiver = arbeidsgiver,
    perioder = perioder,
    arbeidsforEtterPeriode = arbeidsforEtterPeriode,
    hensynArbeidsplassen = hensynArbeidsplassen,
    tiltakArbeidsplassen = tiltakArbeidsplassen,
    innspillArbeidsplassen = innspillArbeidsplassen,
    behandler = behandler,
)

private fun TestApplicationRequest.addAuthorizationHeader(
    audience: String = "dummy-client-id",
    subject: String = "08086912345",
    issuer: String = "https://sts.issuer.net/myid",
    level: String = "Level4",
) {
    addHeader(
        "Authorization",
        "Bearer ${
            generateJWTLoginservice(
                audience = audience,
                subject = subject,
                issuer = issuer,
                level = level
            )
        }"
    )
}

private fun String.minifyApiResponse(): String =
    objectMapper.writeValueAsString(
        objectMapper.readValue(this)
    )
