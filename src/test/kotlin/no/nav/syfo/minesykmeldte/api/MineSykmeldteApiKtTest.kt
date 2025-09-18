package no.nav.syfo.minesykmeldte.api

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.model.Aktivitetsvarsel
import no.nav.syfo.minesykmeldte.model.Arbeidsgiver
import no.nav.syfo.minesykmeldte.model.Behandler
import no.nav.syfo.minesykmeldte.model.Dialogmote
import no.nav.syfo.minesykmeldte.model.Oppfolgingsplan
import no.nav.syfo.minesykmeldte.model.Periode
import no.nav.syfo.minesykmeldte.model.PreviewNySoknad
import no.nav.syfo.minesykmeldte.model.PreviewSykmeldt
import no.nav.syfo.minesykmeldte.model.Soknad
import no.nav.syfo.minesykmeldte.model.Sporsmal
import no.nav.syfo.minesykmeldte.model.Svar
import no.nav.syfo.minesykmeldte.model.Sykmelding
import no.nav.syfo.minesykmeldte.model.UtenlandskSykmelding
import no.nav.syfo.soknad.model.Svartype
import no.nav.syfo.soknad.model.Visningskriterium
import no.nav.syfo.util.addAuthorizationHeader
import no.nav.syfo.util.minifyApiResponse
import no.nav.syfo.util.withKtor
import org.amshove.kluent.shouldBeEqualTo

@ExperimentalTime
class MineSykmeldteApiKtTest :
    FunSpec(
        {
            val mineSykmeldteService = mockk<MineSykmeldteService>()
            val env = mockk<Environment>()

            beforeEach {
                every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
            }

            afterEach { clearMocks(mineSykmeldteService, env) }

            context("/api/mineSykmeldte") {
                test("should get empty list") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        coEvery { mineSykmeldteService.getMineSykmeldte("08086912345") } returns
                            emptyList()
                        runBlocking {
                            val response =
                                client.get("/api/minesykmeldte") { addAuthorizationHeader() }
                            response.status shouldBeEqualTo HttpStatusCode.OK
                            response.bodyAsText() shouldBeEqualTo "[]"
                        }
                    }
                }

                test("should get data in list") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        val startdato = LocalDate.now().minusDays(14)
                        coEvery { mineSykmeldteService.getMineSykmeldte("08086912345") } returns
                            listOf(
                                PreviewSykmeldt(
                                    narmestelederId = "08086912345",
                                    orgnummer = "orgnummer",
                                    orgnavn = "Bedrift AS",
                                    fnr = "fnr",
                                    navn = "navn",
                                    startdatoSykefravar = LocalDate.now().minusDays(14),
                                    friskmeldt = false,
                                    sykmeldinger = emptyList(),
                                    previewSoknader = emptyList(),
                                    dialogmoter = emptyList(),
                                    aktivitetsvarsler = emptyList(),
                                    oppfolgingsplaner = emptyList(),
                                    isPilotUser = false
                                ),
                            )
                        runBlocking {
                            val response =
                                client.get("/api/minesykmeldte") { addAuthorizationHeader() }
                            response.status shouldBeEqualTo HttpStatusCode.OK
                            response.bodyAsText() shouldBeEqualTo
                                """[ {
                          "narmestelederId": "08086912345",
                          "orgnummer": "orgnummer",
                          "orgnavn": "Bedrift AS",
                          "fnr": "fnr",
                          "navn": "navn",
                          "startdatoSykefravar": "$startdato",
                          "friskmeldt": false,
                          "sykmeldinger": [],
                          "previewSoknader": [],
                          "dialogmoter": [],
                          "aktivitetsvarsler": [],
                          "oppfolgingsplaner": [],
                          "isPilotUser": false
                        }
                    ]"""
                                    .minifyApiResponse()
                        }
                    }
                }

                test("should return 401 when missing a valid bearer token") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        coEvery { mineSykmeldteService.getMineSykmeldte("08086912345") } returns
                            emptyList()
                        runBlocking {
                            val response =
                                client.get("/api/minesykmeldte") {
                                    header(
                                        "Authorization",
                                        "Bearer tull",
                                    )
                                }

                            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                }

                test("should return 401 when providing the wrong audience") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        coEvery { mineSykmeldteService.getMineSykmeldte("08086912345") } returns
                            emptyList()

                        runBlocking {
                            val response =
                                client.get("/api/minesykmeldte") {
                                    addAuthorizationHeader(audience = "wrong-dummy-client-id")
                                }

                            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                }

                test("should return 401 when providing the wrong issuer") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        coEvery { mineSykmeldteService.getMineSykmeldte("08086912345") } returns
                            emptyList()
                        runBlocking {
                            val response =
                                client.get("/api/minesykmeldte") {
                                    addAuthorizationHeader(issuer = "https://wrong.issuer.net/myid")
                                }

                            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                }

                test("should return 401 when jwt has the wrong access level") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        coEvery { mineSykmeldteService.getMineSykmeldte("08086912345") } returns
                            emptyList()

                        runBlocking {
                            val response =
                                client.get("/api/minesykmeldte") {
                                    addAuthorizationHeader(
                                        level = "Level3",
                                    )
                                }

                            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                }

                context("given a søknad") {
                    test("should map a ny søknad to correct object") {
                        withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                            val hendelseId = UUID.randomUUID()
                            coEvery { mineSykmeldteService.getMineSykmeldte("08086912345") } returns
                                listOf(
                                    PreviewSykmeldt(
                                        narmestelederId = "08086912345",
                                        orgnummer = "orgnummer",
                                        orgnavn = "Bedrift AS",
                                        fnr = "fnr",
                                        navn = "navn",
                                        startdatoSykefravar = LocalDate.now().minusDays(14),
                                        friskmeldt = false,
                                        sykmeldinger = emptyList(),
                                        previewSoknader =
                                            listOf(
                                                PreviewNySoknad(
                                                    id = "soknad-1-id",
                                                    sykmeldingId = "sykmelding-id-1",
                                                    fom = LocalDate.parse("2020-01-01"),
                                                    tom = LocalDate.parse("2020-02-01"),
                                                    lest = false,
                                                    perioder = listOf(),
                                                    ikkeSendtSoknadVarsel = true,
                                                    ikkeSendtSoknadVarsletDato =
                                                        OffsetDateTime.parse(
                                                            "2020-03-01T10:10:30+02:00",
                                                        ),
                                                ),
                                            ),
                                        dialogmoter =
                                            listOf(
                                                Dialogmote(
                                                    hendelseId,
                                                    "Ny revidert oppfølgingplan",
                                                    OffsetDateTime.parse(
                                                        "2022-03-11T10:15:30+02:00"
                                                    ),
                                                ),
                                            ),
                                        aktivitetsvarsler =
                                            listOf(
                                                Aktivitetsvarsel(
                                                    hendelseId,
                                                    OffsetDateTime.parse(
                                                        "2022-04-09T10:15:30+02:00"
                                                    ),
                                                    null,
                                                ),
                                            ),
                                        oppfolgingsplaner =
                                            listOf(
                                                Oppfolgingsplan(
                                                    hendelseId,
                                                    "ny oppfolgingsplan",
                                                    OffsetDateTime.parse(
                                                        "2022-06-17T10:15:30+02:00"
                                                    ),
                                                ),
                                            ),
                                        isPilotUser = false,
                                    ),
                                )

                            runBlocking {
                                val response =
                                    client.get("/api/minesykmeldte") { addAuthorizationHeader() }

                                response.status shouldBeEqualTo HttpStatusCode.OK
                                response.bodyAsText() shouldBeEqualTo
                                    """
                        [
                          {
                            "narmestelederId": "08086912345",
                            "orgnummer": "orgnummer",
                            "orgnavn": "Bedrift AS",
                            "fnr": "fnr",
                            "navn": "navn",
                            "startdatoSykefravar": "${LocalDate.now().minusDays(14)}",
                            "friskmeldt": false,
                            "sykmeldinger": [],
                            "previewSoknader": [
                              {
                                "lest": false,
                                "ikkeSendtSoknadVarsel": true,
                                "ikkeSendtSoknadVarsletDato": "2020-03-01T10:10:30+02:00",
                                "id": "soknad-1-id",
                                "sykmeldingId": "sykmelding-id-1",
                                "fom": "2020-01-01",
                                "tom": "2020-02-01",
                                "perioder": [],
                                "status": "NY"
                              }
                            ],
                            "dialogmoter":[{"hendelseId": "$hendelseId","tekst":"Ny revidert oppfølgingplan","mottatt":"2022-03-11T10:15:30+02:00"}],
                            "aktivitetsvarsler": [{"hendelseId":"$hendelseId","mottatt":"2022-04-09T10:15:30+02:00","lest":null}],
                            "oppfolgingsplaner": [{"hendelseId": "$hendelseId","tekst":"ny oppfolgingsplan","mottatt":"2022-06-17T10:15:30+02:00"}],
                            "isPilotUser": false
                          }
                        ]"""
                                        .minifyApiResponse()
                            }
                        }
                    }
                }
            }

            context("/api/sykmelding/{id}") {
                test("should respond with 404 Not Found if not found in the database ") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        coEvery {
                            mineSykmeldteService.getSykmelding(
                                "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
                                any(),
                            )
                        } returns null
                        runBlocking {
                            val response =
                                client.get("/api/sykmelding/7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657") {
                                    addAuthorizationHeader()
                                }

                            response.status shouldBeEqualTo HttpStatusCode.NotFound
                            response.bodyAsText() shouldBeEqualTo
                                """{ "message": "Sykmeldingen finnes ikke" }""".minifyApiResponse()

                            coVerify(exactly = 1) {
                                mineSykmeldteService.getSykmelding(
                                    any(),
                                    any(),
                                )
                            }
                        }
                    }
                }

                test("should respond with the correct content if found") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        coEvery {
                            mineSykmeldteService.getSykmelding(
                                "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
                                any(),
                            )
                        } returns
                            createSykmeldingTestData(
                                id = "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
                                startdatoSykefravar = LocalDate.parse("2021-01-01"),
                                kontaktDato = LocalDate.parse("2021-01-01"),
                                behandletTidspunkt = LocalDate.parse("2021-01-02"),
                                sendtTilArbeidsgiverDato =
                                    OffsetDateTime.parse("2022-05-09T13:20:04+00:00"),
                            )

                        runBlocking {
                            val response =
                                client.get("/api/sykmelding/7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657") {
                                    addAuthorizationHeader()
                                }

                            response.status shouldBeEqualTo HttpStatusCode.OK
                            response.bodyAsText() shouldBeEqualTo
                                """
                        {
                          "id": "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
                          "startdatoSykefravar": "2021-01-01",
                          "kontaktDato": "2021-01-01",
                          "navn": "navn",
                          "fnr": "fnr",
                          "lest": false,
                          "behandletTidspunkt":"2021-01-02",
                          "arbeidsgiver": {
                            "navn": "Arbeid G. Iversen"
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
                          },
                          "sendtTilArbeidsgiverDato":"2022-05-09T13:20:04Z",
                          "utenlandskSykmelding":null,
                          "egenmeldingsdager":null
                        }
                    """
                                    .minifyApiResponse()
                        }
                        coVerify(exactly = 1) {
                            mineSykmeldteService.getSykmelding(
                                any(),
                                any(),
                            )
                        }
                    }
                }
            }

            context("/api/hendelser/read") {
                test("Should get 200 OK") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        coEvery {
                            mineSykmeldteService.markAllSykmeldingerAndSoknaderRead(any())
                        } returns Unit

                        runBlocking {
                            val response =
                                client.put("/api/hendelser/read") { addAuthorizationHeader() }

                            response.status shouldBeEqualTo HttpStatusCode.OK
                            response.bodyAsText() shouldBeEqualTo
                                """{ "message": "Markert som lest" }""".minifyApiResponse()
                        }
                        coVerify(exactly = 1) {
                            mineSykmeldteService.markAllSykmeldingerAndSoknaderRead(any())
                        }
                    }
                }
                test("Unauthorized") {
                    withKtor({ registerMineSykmeldteApi(mineSykmeldteService) }) {
                        coEvery {
                            mineSykmeldteService.markAllSykmeldingerAndSoknaderRead(any())
                        } returns Unit

                        runBlocking {
                            val response = client.put("/api/hendelser/read")

                            response.status shouldBeEqualTo HttpStatusCode.Unauthorized

                            coVerify(exactly = 0) {
                                mineSykmeldteService.markAllSykmeldingerAndSoknaderRead(any())
                            }
                        }
                    }
                }
                context("/api/soknad/{id}") {
                    test("should respond with 404 Not Found if not found in the database ") {
                        withKtor(
                            { registerMineSykmeldteApi(mineSykmeldteService) },
                        ) {
                            coEvery {
                                mineSykmeldteService.getSoknad(
                                    "7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657",
                                    any(),
                                )
                            } returns null

                            runBlocking {
                                val response =
                                    client.get("/api/soknad/7eac0c9d-eb1e-4b5f-82e0-aa4961fd5657") {
                                        addAuthorizationHeader()
                                    }

                                response.status shouldBeEqualTo HttpStatusCode.NotFound
                                response.bodyAsText() shouldBeEqualTo
                                    """{ "message": "Søknaden finnes ikke" }""".minifyApiResponse()

                                coVerify(exactly = 1) {
                                    mineSykmeldteService.getSoknad(
                                        any(),
                                        any(),
                                    )
                                }
                            }
                        }
                    }

                    test("should respond with the correct content if found") {
                        withKtor(
                            { registerMineSykmeldteApi(mineSykmeldteService) },
                        ) {
                            val sporsmal =
                                listOf(
                                    Sporsmal(
                                        id = "890342785232",
                                        tag = "Arbeid",
                                        min = "2021-10-03",
                                        max = "2021-10-06",
                                        sporsmalstekst = "Har du vært på ferie?",
                                        undertekst = null,
                                        svartype = Svartype.JA_NEI,
                                        kriterieForVisningAvUndersporsmal =
                                            Visningskriterium.CHECKED,
                                        svar =
                                            listOf(
                                                Svar(
                                                    verdi = "Nei",
                                                ),
                                            ),
                                        undersporsmal = emptyList(),
                                    ),
                                )

                            coEvery {
                                mineSykmeldteService.getSoknad(
                                    "d9ca08ca-bdbf-4571-ba4f-109c3642047b",
                                    any(),
                                )
                            } returns
                                createSoknadTestData(
                                    id = "d9ca08ca-bdbf-4571-ba4f-109c3642047b",
                                    sykmeldingId = "772e674d-0422-4a5e-b779-a8819abf5959",
                                    tom = LocalDate.parse("2021-01-01"),
                                    fom = LocalDate.parse("2020-12-01"),
                                    sporsmal = sporsmal,
                                )

                            runBlocking {
                                val response =
                                    client.get("/api/soknad/d9ca08ca-bdbf-4571-ba4f-109c3642047b") {
                                        addAuthorizationHeader()
                                    }

                                response.status shouldBeEqualTo HttpStatusCode.OK
                                response.bodyAsText() shouldBeEqualTo
                                    """                      
                       {
                         "id": "d9ca08ca-bdbf-4571-ba4f-109c3642047b",
                         "sykmeldingId": "772e674d-0422-4a5e-b779-a8819abf5959",
                         "fom":"2020-12-01",
                         "tom":"2021-01-01",
                         "navn": "Navn N. Navnessen",
                         "fnr": "08088012345",
                         "lest": false,
                         "sendtDato":"2022-05-09T08:56:24",
                         "sendtTilNavDato":"2022-05-09T08:56:24",
                         "korrigererSoknadId":null,
                         "korrigertBySoknadId": "0422-4a5e-b779-a8819abf",
                         "perioder": [],
                         "sporsmal": [{
                            "id": "890342785232",
                            "tag": "Arbeid",
                            "min": "2021-10-03",
                            "max": "2021-10-06",
                            "sporsmalstekst": "Har du vært på ferie?",
                            "undertekst": null,
                            "svartype": "JA_NEI",
                            "kriterieForVisningAvUndersporsmal": "CHECKED",
                            "svar": [{
                                "verdi": "Nei"
                            }],
                            "undersporsmal": []
                         }]
                      }
                    """
                                        .minifyApiResponse()

                                coVerify(exactly = 1) {
                                    mineSykmeldteService.getSoknad(
                                        any(),
                                        any(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )

fun createSoknadTestData(
    id: String = UUID.randomUUID().toString(),
    sykmeldingId: String = UUID.randomUUID().toString(),
    navn: String = "Navn N. Navnessen",
    fnr: String = "08088012345",
    lest: Boolean = false,
    sendtDato: LocalDateTime = LocalDateTime.parse("2022-05-09T08:56:24"),
    sendtTilNavoDato: LocalDateTime = LocalDateTime.parse("2022-05-09T08:56:24"),
    tom: LocalDate = LocalDate.now(),
    fom: LocalDate = LocalDate.parse("2021-05-01"),
    korrigererSoknadId: String? = null,
    korrigertBySoknadId: String = "0422-4a5e-b779-a8819abf",
    sporsmal: List<Sporsmal>,
) =
    Soknad(
        id = id,
        sykmeldingId = sykmeldingId,
        navn = navn,
        fnr = fnr,
        tom = tom,
        fom = fom,
        lest = lest,
        sendtDato = sendtDato,
        sendtTilNavDato = sendtTilNavoDato,
        korrigererSoknadId = korrigererSoknadId,
        korrigertBySoknadId = korrigertBySoknadId,
        perioder = listOf(),
        sporsmal = sporsmal,
    )

fun createSykmeldingTestData(
    id: String = UUID.randomUUID().toString(),
    startdatoSykefravar: LocalDate = LocalDate.now(),
    kontaktDato: LocalDate = LocalDate.now(),
    navn: String = "navn",
    fnr: String = "fnr",
    lest: Boolean = false,
    arbeidsgiver: Arbeidsgiver =
        Arbeidsgiver(
            navn = "Arbeid G. Iversen",
        ),
    perioder: List<Periode> = emptyList(),
    arbeidsforEtterPeriode: Boolean = false,
    hensynArbeidsplassen: String = "hensynArbeidsplassen",
    tiltakArbeidsplassen: String = "tiltakArbeidsplassen",
    innspillArbeidsplassen: String = "innspillArbeidsplassen",
    behandler: Behandler =
        Behandler(
            navn = "Beh. Handler",
            hprNummer = "80802721231",
            telefon = "81549300",
        ),
    behandletTidspunkt: LocalDate = LocalDate.now(),
    sendtTilArbeidsgiverDato: OffsetDateTime =
        OffsetDateTime.now(
            ZoneOffset.UTC,
        ),
    land: String? = null,
): Sykmelding =
    Sykmelding(
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
        behandletTidspunkt = behandletTidspunkt,
        sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
        utenlandskSykmelding = land?.let { UtenlandskSykmelding(it) },
        egenmeldingsdager = null,
    )
