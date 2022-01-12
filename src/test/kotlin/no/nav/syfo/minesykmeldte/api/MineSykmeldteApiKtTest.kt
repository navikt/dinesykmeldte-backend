package no.nav.syfo.minesykmeldte.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.Environment
import no.nav.syfo.kafka.felles.SoknadsstatusDTO
import no.nav.syfo.kafka.felles.SoknadstypeDTO
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.model.Arbeidsgiver
import no.nav.syfo.minesykmeldte.model.Behandler
import no.nav.syfo.minesykmeldte.model.NySoknad
import no.nav.syfo.minesykmeldte.model.Periode
import no.nav.syfo.minesykmeldte.model.PreviewSykmeldt
import no.nav.syfo.minesykmeldte.model.Soknad
import no.nav.syfo.minesykmeldte.model.SoknadDetails
import no.nav.syfo.minesykmeldte.model.Sykmelding
import no.nav.syfo.util.addAuthorizationHeader
import no.nav.syfo.util.minifyApiResponse
import no.nav.syfo.util.withKtor
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
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

    withKtor(env, {
        registerMineSykmeldteApi(mineSykmeldteService)
    }) {
        describe("/api/mineSykmeldte") {
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

            describe("given a søknad") {
                it("should map a ny søknad to correct object") {
                    every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns listOf(
                        PreviewSykmeldt(
                            narmestelederId = "08086912345",
                            orgnummer = "orgnummer",
                            fnr = "fnr",
                            navn = "navn",
                            startdatoSykefravar = LocalDate.now().minusDays(14),
                            friskmeldt = false,
                            previewSykmeldinger = emptyList(),
                            previewSoknader = listOf(
                                NySoknad(
                                    id = "soknad-1-id",
                                    sykmeldingId = "sykmelding-id-1",
                                    frist = LocalDate.parse("2020-05-05"),
                                    fom = LocalDate.parse("2020-01-01"),
                                    tom = LocalDate.parse("2020-02-01"),
                                    varsel = true,
                                )
                            ),
                        )
                    )
                    with(
                        handleRequest(HttpMethod.Get, "/api/minesykmeldte") { addAuthorizationHeader() }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        response.content shouldBeEqualTo """
                        [
                          {
                            "narmestelederId": "08086912345",
                            "orgnummer": "orgnummer",
                            "fnr": "fnr",
                            "navn": "navn",
                            "startdatoSykefravar": "${LocalDate.now().minusDays(14)}",
                            "friskmeldt": false,
                            "previewSykmeldinger": [],
                            "previewSoknader": [
                              {
                                "frist": "2020-05-05",
                                "varsel": true,
                                "id": "soknad-1-id",
                                "sykmeldingId": "sykmelding-id-1",
                                "fom": "2020-01-01",
                                "tom": "2020-02-01",
                                "status": "NY"
                              }
                            ]
                          }
                        ]""".minifyApiResponse()
                    }
                }
            }
        }

        describe("/api/sykmelding/{id}") {
            it("should respond with 404 Not Found if not found in the database ") {
                every {
                    mineSykmeldteService.getSykmelding(
                        "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
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

            it("should respond with the correct content if found") {
                every {
                    mineSykmeldteService.getSykmelding(
                        "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
                        any()
                    )
                } returns createSykmeldingTestData(
                    id = "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
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
                          "id": "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
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

        describe("/api/soknad/{id}") {
            it("should respond with 404 Not Found if not found in the database ") {
                every {
                    mineSykmeldteService.getSoknad(
                        "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
                        any()
                    )
                } returns null
                with(
                    handleRequest(HttpMethod.Get, "/api/soknad/7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657") {
                        addAuthorizationHeader()
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                    response.content shouldBeEqualTo """{ "message": "Søknaden finnes ikke" }""".minifyApiResponse()
                }
                verify(exactly = 1) { mineSykmeldteService.getSoknad(any(), any()) }
            }

            it("should respond with the correct content if found") {
                every {
                    mineSykmeldteService.getSoknad(
                        "d9ca08ca-bdbf-4571-ba4f-109c3642047b",
                        any()
                    )
                } returns createSoknadTestData(
                    id = "d9ca08ca-bdbf-4571-ba4f-109c3642047b",
                    sykmeldingId = "772e674d-0422-4a5e-b779-a8819abf5959",
                    tom = LocalDate.parse("2021-01-01"),
                    sendtDato = LocalDate.parse("2021-01-01"),
                )
                with(
                    handleRequest(HttpMethod.Get, "/api/soknad/d9ca08ca-bdbf-4571-ba4f-109c3642047b") {
                        addAuthorizationHeader()
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.content shouldBeEqualTo """                      
                       {
                         "id": "d9ca08ca-bdbf-4571-ba4f-109c3642047b",
                         "sykmeldingId": "772e674d-0422-4a5e-b779-a8819abf5959",
                         "navn": "Navn N. Navnessen",
                         "fnr": "08088012345",
                         "lest": false,
                         "orgnummer": "123456789",
                         "sendtDato": "2021-01-01",
                         "tom": "2021-01-01",
                         "details": {
                           "type": "ARBEIDSLEDIG",
                           "status": "NY"
                         }
                      }
                    """.minifyApiResponse()
                }
                verify(exactly = 1) { mineSykmeldteService.getSoknad(any(), any()) }
            }
        }
    }
})

fun createSoknadTestData(
    id: String = UUID.randomUUID().toString(),
    sykmeldingId: String = UUID.randomUUID().toString(),
    navn: String = "Navn N. Navnessen",
    fnr: String = "08088012345",
    lest: Boolean = false,
    orgnummer: String = "123456789",
    sendtDato: LocalDate = LocalDate.now(),
    tom: LocalDate = LocalDate.now(),
    details: SoknadDetails = SoknadDetails(
        type = SoknadstypeDTO.ARBEIDSLEDIG,
        status = SoknadsstatusDTO.NY,
    ),
) = Soknad(
    id = id,
    sykmeldingId = sykmeldingId,
    navn = navn,
    fnr = fnr,
    lest = lest,
    orgnummer = orgnummer,
    sendtDato = sendtDato,
    tom = tom,
    details = details,
)

fun createSykmeldingTestData(
    id: String = UUID.randomUUID().toString(),
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
    ),
): Sykmelding = Sykmelding(
    id = id,
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
