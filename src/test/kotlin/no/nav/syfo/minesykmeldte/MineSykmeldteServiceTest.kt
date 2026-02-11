package no.nav.syfo.minesykmeldte

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.minesykmeldte.db.MinSykmeldtDbModel
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.model.AktivitetIkkeMulig
import no.nav.syfo.minesykmeldte.model.ArbeidsrelatertArsakEnum
import no.nav.syfo.minesykmeldte.model.Avventende
import no.nav.syfo.minesykmeldte.model.Behandlingsdager
import no.nav.syfo.minesykmeldte.model.Gradert
import no.nav.syfo.minesykmeldte.model.Periode
import no.nav.syfo.minesykmeldte.model.PeriodeEnum
import no.nav.syfo.minesykmeldte.model.PreviewFremtidigSoknad
import no.nav.syfo.minesykmeldte.model.PreviewNySoknad
import no.nav.syfo.minesykmeldte.model.PreviewSendtSoknad
import no.nav.syfo.minesykmeldte.model.Reisetilskudd
import no.nav.syfo.minesykmeldte.model.SoknadStatus
import no.nav.syfo.minesykmeldte.model.Svar
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.soknad.model.Soknad
import no.nav.syfo.soknad.model.SoknadStatus.FREMTIDIG
import no.nav.syfo.soknad.model.SoknadStatus.NY
import no.nav.syfo.soknad.model.SoknadStatus.SENDT
import no.nav.syfo.soknad.model.Soknadsperiode
import no.nav.syfo.soknad.model.Sporsmal
import no.nav.syfo.soknad.model.Svartype
import no.nav.syfo.soknad.model.Sykmeldingstype
import no.nav.syfo.soknad.model.Visningskriterium
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.sykmelding.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.sykmelding.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.model.sykmelding.model.ArbeidsrelatertArsakDTO
import no.nav.syfo.sykmelding.model.sykmelding.model.ArbeidsrelatertArsakTypeDTO
import no.nav.syfo.sykmelding.model.sykmelding.model.GradertDTO
import no.nav.syfo.sykmelding.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.util.createArbeidsgiverSykmelding
import no.nav.syfo.util.createSoknad
import no.nav.syfo.util.createSykmeldingsperiode
import no.nav.syfo.util.objectMapper
import no.nav.syfo.util.shouldBeInstance
import no.nav.syfo.util.toSoknadDbModel
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBe
import org.amshove.kluent.shouldNotBeNull
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class MineSykmeldteServiceTest :
    FunSpec(
        {
            val mineSykmeldteDb = mockk<MineSykmeldteDb>(relaxed = true)
            val mineSykmeldtService = MineSykmeldteService(mineSykmeldteDb)

            afterEach { clearMocks(mineSykmeldteDb) }

            context("getMineSykmeldte") {
                test("Should get mine sykmeldte with oppfolgingsplan hendelse") {
                    val hendelseid = UUID.randomUUID()
                    coEvery { mineSykmeldteDb.getHendelser("1") } returns
                        listOf(
                            HendelseDbModel(
                                id = "12",
                                pasientFnr = "prefix-0",
                                orgnummer = "orgnummer",
                                oppgavetype = "OPPFOLGINGSPLAN_OPPRETTET",
                                lenke = "localhost",
                                tekst = "Ny oppfolgingsplan",
                                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                                utlopstidspunkt = null,
                                ferdigstilt = false,
                                ferdigstiltTimestamp = null,
                                hendelseId = hendelseid,
                            ),
                        )
                    coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                        getSykmeldtData(1, sykmeldtFnrPrefix = "prefix")
                    val sykmeldte = runBlocking { mineSykmeldtService.getMineSykmeldte("1") }
                    sykmeldte.size shouldBeEqualTo 1
                    val oppfolningsplaner = sykmeldte.first().oppfolgingsplaner
                    oppfolningsplaner.size shouldBeEqualTo 1
                    oppfolningsplaner.first().hendelseId shouldBeEqualTo hendelseid
                    oppfolningsplaner.first().tekst shouldBeEqualTo "Ny oppfolgingsplan"
                }
                test("Should get mine sykmeldte with hendelser") {
                    coEvery { mineSykmeldteDb.getHendelser("1") } returns
                        listOf(
                            HendelseDbModel(
                                id = "12",
                                pasientFnr = "avdeling-1-0",
                                orgnummer = "orgnummer",
                                oppgavetype = "DIALOGMOTE_INNKALLING",
                                lenke = "localhost",
                                tekst = "Innkalling til dialogmøte",
                                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                                utlopstidspunkt = null,
                                ferdigstilt = false,
                                ferdigstiltTimestamp = null,
                                hendelseId = UUID.randomUUID(),
                            ),
                        )
                    coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                        getSykmeldtData(
                            sykmeldte = 2,
                            sykmeldinger =
                                listOf(
                                    createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                                    createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                                ),
                            sykmeldtFnrPrefix = "avdeling-1",
                            soknader = 1,
                        )
                    runBlocking {
                        val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                        mineSykmeldte shouldHaveSize 2
                        mineSykmeldte.first { it.fnr == "avdeling-1-0" }.dialogmoter shouldHaveSize
                            1
                        mineSykmeldte.first { it.fnr == "avdeling-1-1" }.dialogmoter shouldHaveSize
                            0
                    }
                }

                test("Should get empty list") {
                    coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns emptyList()
                    coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                    runBlocking { mineSykmeldtService.getMineSykmeldte("1").size shouldBeEqualTo 0 }
                }

                test("should get one sykmeldt") {
                    coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                        getSykmeldtData(1, sykmeldtFnrPrefix = "prefix")
                    coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                    runBlocking {
                        val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                        mineSykmeldte.size shouldBeEqualTo 1
                        val periode =
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .first()
                        periode.shouldBeInstance<AktivitetIkkeMulig>()
                    }
                }

                test("Should get one sykmeldt with one IKKE_SENDT_SOKNAD") {
                    val sykmeldtData =
                        getSykmeldtData(1, sykmeldtFnrPrefix = "prefix", soknader = 1).map {
                            it.copy(soknad = it.soknad!!.copy(status = NY))
                        }

                    coEvery { mineSykmeldteDb.getHendelser("1") } returns
                        listOf(
                            HendelseDbModel(
                                id = sykmeldtData.first().soknad!!.id,
                                pasientFnr = "prefix-0",
                                orgnummer = "orgnummer",
                                oppgavetype = "IKKE_SENDT_SOKNAD",
                                lenke = null,
                                tekst = null,
                                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                                utlopstidspunkt = null,
                                ferdigstilt = false,
                                ferdigstiltTimestamp = null,
                                hendelseId = UUID.randomUUID(),
                            ),
                        )

                    coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns sykmeldtData
                    runBlocking {
                        val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                        mineSykmeldte.size shouldBeEqualTo 1
                        mineSykmeldte
                            .first()
                            .sykmeldinger
                            .first()
                            .perioder
                            .first()
                            .type shouldBeEqualTo PeriodeEnum.AKTIVITET_IKKE_MULIG
                        (mineSykmeldte.first().previewSoknader.first() as PreviewNySoknad)
                            .ikkeSendtSoknadVarsel shouldBeEqualTo true
                    }
                }

                test("should group sykmeldinger and søknader by sykmeldt") {
                    coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                    coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                        getSykmeldtData(
                            sykmeldte = 3,
                            sykmeldinger =
                                listOf(
                                    createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                                    createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                                ),
                            sykmeldtFnrPrefix = "avdeling-1",
                            soknader = 1,
                        ) +
                        getSykmeldtData(
                            sykmeldte = 2,
                            sykmeldinger =
                                listOf(
                                    createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                                    createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                                    createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                                ),
                            sykmeldtFnrPrefix = "avdeling-2",
                            soknader = 0,
                        )

                    runBlocking {
                        val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                        mineSykmeldte shouldHaveSize 5
                    }
                }

                context("sykmeldt") {
                    test("should not be friskmeldt if the latest sykmeldt period is tomorrow") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().minusDays(24),
                                                tom = LocalDate.now().plusDays(1),
                                                type = PeriodetypeDTO.GRADERT,
                                                gradert = GradertDTO(50, false),
                                            ),
                                        ),
                                    ),
                                ),
                                sykmeldtFnrPrefix = "prefix",
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte shouldHaveSize 1
                            mineSykmeldte.first().friskmeldt shouldBe false
                        }
                    }

                    test("should be friskmeldt if the latest sykmeldt period is 1 day ago") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().minusDays(24),
                                                tom = LocalDate.now().minusDays(1),
                                                type = PeriodetypeDTO.GRADERT,
                                                gradert = GradertDTO(50, false),
                                            ),
                                        ),
                                    ),
                                ),
                                sykmeldtFnrPrefix = "prefix",
                            )

                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte shouldHaveSize 1
                            mineSykmeldte.first().friskmeldt shouldBe true
                        }
                    }
                }

                context("given different types") {
                    test("should get one sykmeldt with 50% type") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now(),
                                                tom = LocalDate.now().plusDays(4),
                                                type = PeriodetypeDTO.GRADERT,
                                                gradert = GradertDTO(50, false),
                                            ),
                                        ),
                                    ),
                                ),
                                sykmeldtFnrPrefix = "prefix",
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .first()
                                .type shouldBeEqualTo PeriodeEnum.GRADERT
                        }
                    }

                    test("should get one sykmeldt with 20% type") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now(),
                                                tom = LocalDate.now().plusDays(4),
                                                type = PeriodetypeDTO.GRADERT,
                                                gradert = GradertDTO(20, false),
                                            ),
                                        ),
                                    ),
                                ),
                                sykmeldtFnrPrefix = "prefix",
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .first()
                                .type shouldBeEqualTo PeriodeEnum.GRADERT
                        }
                    }

                    test("should get one sykmeldt with avventende") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now(),
                                                tom = LocalDate.now().plusDays(4),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                        ),
                                    ),
                                ),
                                sykmeldtFnrPrefix = "prefix",
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .first()
                                .type shouldBeEqualTo PeriodeEnum.AVVENTENDE
                        }
                    }

                    test("should get one sykmeldt with behandlingsdager") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now(),
                                                tom = LocalDate.now().plusDays(4),
                                                type = PeriodetypeDTO.BEHANDLINGSDAGER,
                                                gradert = null,
                                            ),
                                        ),
                                    ),
                                ),
                                sykmeldtFnrPrefix = "prefix",
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .first()
                                .type shouldBeEqualTo PeriodeEnum.BEHANDLINGSDAGER
                        }
                    }

                    test("should get one sykmeldt with reisetilskudd") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now(),
                                                tom = LocalDate.now().plusDays(4),
                                                type = PeriodetypeDTO.REISETILSKUDD,
                                                gradert = null,
                                            ),
                                        ),
                                    ),
                                ),
                                sykmeldtFnrPrefix = "prefix",
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .first()
                                .type shouldBeEqualTo PeriodeEnum.REISETILSKUDD
                        }
                    }

                    test("should pick the correct period when one period is now") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().minusDays(15),
                                                tom = LocalDate.now().minusDays(11),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().minusDays(1),
                                                tom = LocalDate.now().plusDays(2),
                                                type = PeriodetypeDTO.REISETILSKUDD,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().plusDays(5),
                                                tom = LocalDate.now().plusDays(10),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .elementAt(1)
                                .type shouldBeEqualTo PeriodeEnum.REISETILSKUDD
                        }
                    }

                    test("should pick the correct period when now is end of period") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().minusDays(15),
                                                tom = LocalDate.now().minusDays(11),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().minusDays(3),
                                                tom = LocalDate.now(),
                                                type = PeriodetypeDTO.REISETILSKUDD,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().plusDays(5),
                                                tom = LocalDate.now().plusDays(10),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .elementAt(1)
                                .type shouldBeEqualTo PeriodeEnum.REISETILSKUDD
                        }
                    }

                    test("should pick the correct period when now is start of period") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().minusDays(15),
                                                tom = LocalDate.now().minusDays(11),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now(),
                                                tom = LocalDate.now().plusDays(3),
                                                type = PeriodetypeDTO.REISETILSKUDD,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().plusDays(5),
                                                tom = LocalDate.now().plusDays(10),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .elementAt(1)
                                .type shouldBeEqualTo PeriodeEnum.REISETILSKUDD
                        }
                    }

                    test(
                        "should pick latest period when there are periods in the past, but one in the future",
                    ) {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().minusDays(20),
                                                tom = LocalDate.now().minusDays(15),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().minusDays(14),
                                                tom = LocalDate.now().minusDays(5),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().plusDays(1),
                                                tom = LocalDate.now().plusDays(4),
                                                type = PeriodetypeDTO.REISETILSKUDD,
                                                gradert = null,
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .last()
                                .type shouldBeEqualTo PeriodeEnum.REISETILSKUDD
                        }
                    }

                    test("should pick the nearest future period, if all in the future") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            getSykmeldtData(
                                1,
                                listOf(
                                    createArbeidsgiverSykmelding(
                                        UUID.randomUUID().toString(),
                                        listOf(
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().plusDays(1),
                                                tom = LocalDate.now().plusDays(4),
                                                type = PeriodetypeDTO.REISETILSKUDD,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().plusDays(5),
                                                tom = LocalDate.now().plusDays(14),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                            createSykmeldingsperiode(
                                                aktivitetIkkeMulig = null,
                                                behandlingsdager = 0,
                                                fom = LocalDate.now().plusDays(15),
                                                tom = LocalDate.now().plusDays(20),
                                                type = PeriodetypeDTO.AVVENTENDE,
                                                gradert = null,
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        runBlocking {
                            val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                            mineSykmeldte.size shouldBeEqualTo 1
                            mineSykmeldte
                                .first()
                                .sykmeldinger
                                .first()
                                .perioder
                                .first()
                                .type shouldBeEqualTo PeriodeEnum.REISETILSKUDD
                        }
                    }
                }

                context("when mapping søknader") {
                    test("should map to a new søknad and use tom if date is latest") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        val soknad =
                            createSoknad("soknad-id", "sykmeldingId")
                                .copy(
                                    status = NY,
                                    tom = LocalDate.parse("2020-05-02"),
                                )

                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            listOf(
                                MinSykmeldtDbModel(
                                    narmestelederId = UUID.randomUUID().toString(),
                                    sykmeldtFnr = "080806933221",
                                    orgnummer = "orgnummer",
                                    sykmeldtNavn = "Navn",
                                    startDatoSykefravar = LocalDate.now(),
                                    orgNavn = "orgnavn",
                                    sykmeldingId = "sykmeldingId",
                                    sykmelding = createArbeidsgiverSykmelding("sykmeldingId"),
                                    soknad = soknad,
                                    lestSykmelding = false,
                                    lestSoknad = false,
                                    sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC),
                                    egenmeldingsdager =
                                        listOf(
                                            LocalDate.parse("2020-04-01"),
                                            LocalDate.parse("2020-04-02"),
                                        ),
                                ),
                            )
                        runBlocking {
                            val mineSykeldte = mineSykmeldtService.getMineSykmeldte("1")
                            val mappedSoknad = mineSykeldte[0].previewSoknader[0]

                            mappedSoknad.shouldBeInstance<PreviewNySoknad>()
                            mappedSoknad.lest shouldBeEqualTo false
                        }
                    }

                    test("should map to a new søknad and use opprettet if date is latest") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        val soknad =
                            createSoknad("soknad-id", "sykmeldingId")
                                .copy(
                                    status = NY,
                                    tom = LocalDate.parse("2020-05-02"),
                                )

                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            listOf(
                                MinSykmeldtDbModel(
                                    narmestelederId = UUID.randomUUID().toString(),
                                    sykmeldtFnr = "080806933221",
                                    orgnummer = "orgnummer",
                                    sykmeldtNavn = "Navn",
                                    startDatoSykefravar = LocalDate.now(),
                                    orgNavn = "orgnavn",
                                    sykmeldingId = "sykmeldingId",
                                    sykmelding = createArbeidsgiverSykmelding("sykmeldingId"),
                                    soknad = soknad,
                                    lestSykmelding = false,
                                    lestSoknad = true,
                                    sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC),
                                    egenmeldingsdager =
                                        listOf(
                                            LocalDate.parse("2020-04-01"),
                                            LocalDate.parse("2020-04-02"),
                                        ),
                                ),
                            )
                        runBlocking {
                            val mineSykeldte = mineSykmeldtService.getMineSykmeldte("1")
                            val mappedSoknad = mineSykeldte[0].previewSoknader[0]

                            mappedSoknad.shouldBeInstance<PreviewNySoknad>()
                            mappedSoknad.lest shouldBeEqualTo true
                        }
                    }

                    test("should map to a sendt søknad") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        val soknad =
                            createSoknad("soknad-id", "sykmeldingId")
                                .copy(
                                    status = SENDT,
                                    korrigerer = "korrigerer-id",
                                    sendtArbeidsgiver =
                                        LocalDateTime.parse("2020-06-07T19:34:50.63"),
                                )

                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            listOf(
                                MinSykmeldtDbModel(
                                    narmestelederId = UUID.randomUUID().toString(),
                                    sykmeldtFnr = "080806933221",
                                    orgnummer = "orgnummer",
                                    sykmeldtNavn = "Navn",
                                    startDatoSykefravar = LocalDate.now(),
                                    orgNavn = "orgnavn",
                                    sykmeldingId = "sykmeldingId",
                                    sykmelding = createArbeidsgiverSykmelding("sykmeldingId"),
                                    soknad = soknad,
                                    lestSykmelding = false,
                                    lestSoknad = true,
                                    sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC),
                                    egenmeldingsdager = null,
                                ),
                            )
                        runBlocking {
                            val mineSykeldte = mineSykmeldtService.getMineSykmeldte("1")
                            val mappedSoknad = mineSykeldte[0].previewSoknader[0]

                            mappedSoknad.shouldBeInstance<PreviewSendtSoknad>()
                            mappedSoknad.lest shouldBeEqualTo true
                            mappedSoknad.korrigererSoknadId shouldBeEqualTo "korrigerer-id"
                            mappedSoknad.sendtDato shouldBeEqualTo
                                LocalDateTime.parse("2020-06-07T19:34:50.63")
                        }
                    }

                    test("Should not get Korrigert soknad") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        val korrigertSoknad =
                            createSoknad("soknad-id-korrigert", "sykmeldingId")
                                .copy(
                                    status = SENDT,
                                )
                        val korrigererSoknad =
                            createSoknad("soknad-id-korrigerer", "sykmeldingId")
                                .copy(
                                    status = SENDT,
                                    korrigerer = "soknad-id-korrigert",
                                )
                        val sendtSoknad =
                            createSoknad("soknad-id-sendt", "sykmeldingId")
                                .copy(
                                    status = SENDT,
                                )
                        val mineSykmeldteModel =
                            MinSykmeldtDbModel(
                                narmestelederId = UUID.randomUUID().toString(),
                                sykmeldtFnr = "080806933221",
                                orgnummer = "orgnummer",
                                sykmeldtNavn = "Navn",
                                startDatoSykefravar = LocalDate.now(),
                                orgNavn = "orgnavn",
                                sykmeldingId = "sykmeldingId",
                                sykmelding = createArbeidsgiverSykmelding("sykmeldingId"),
                                soknad = korrigertSoknad,
                                lestSykmelding = false,
                                lestSoknad = true,
                                sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC),
                                egenmeldingsdager = null,
                            )

                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            listOf(
                                mineSykmeldteModel,
                                mineSykmeldteModel.copy(soknad = korrigererSoknad),
                                mineSykmeldteModel.copy(soknad = sendtSoknad),
                            )

                        val mineSykmeldte =
                            runBlocking {
                                mineSykmeldtService.getMineSykmeldte("1")
                            }
                        mineSykmeldte.size shouldBeEqualTo 1

                        val soknader = mineSykmeldte.first().previewSoknader
                        soknader.size shouldBeEqualTo 2

                        val sendte = soknader.mapNotNull { it as? PreviewSendtSoknad }
                        sendte.size shouldBeEqualTo 2
                        val korrigerer = sendte.first { it.id == "soknad-id-korrigerer" }
                        korrigerer.korrigererSoknadId shouldBeEqualTo "soknad-id-korrigert"
                        korrigerer.status shouldBeEqualTo SoknadStatus.SENDT

                        val sendt = sendte.first { it.id == "soknad-id-sendt" }
                        sendt.status shouldBeEqualTo SoknadStatus.SENDT
                        sendt.korrigererSoknadId shouldBeEqualTo null
                    }

                    test("should map to a fremtidig søknad") {
                        coEvery { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                        val soknad =
                            createSoknad("soknad-id", "sykmeldingId")
                                .copy(
                                    status = FREMTIDIG,
                                )

                        coEvery { mineSykmeldteDb.getMineSykmeldte("1") } returns
                            listOf(
                                MinSykmeldtDbModel(
                                    narmestelederId = UUID.randomUUID().toString(),
                                    sykmeldtFnr = "080806933221",
                                    orgnummer = "orgnummer",
                                    sykmeldtNavn = "Navn",
                                    startDatoSykefravar = LocalDate.now(),
                                    orgNavn = "orgnavn",
                                    sykmeldingId = "sykmeldingId",
                                    sykmelding = createArbeidsgiverSykmelding("sykmeldingId"),
                                    soknad = soknad,
                                    lestSykmelding = false,
                                    lestSoknad = true,
                                    sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC),
                                    egenmeldingsdager = null,
                                ),
                            )
                        runBlocking {
                            val mineSykeldte = mineSykmeldtService.getMineSykmeldte("1")
                            val mappedSoknad = mineSykeldte[0].previewSoknader[0]

                            mappedSoknad.shouldBeInstance<PreviewFremtidigSoknad>()
                        }
                    }
                }
            }

            context("getSykmelding") {
                test("should map to aktivitetIkkeMulig") {
                    val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
                    val aktivitetIkkeMulig =
                        AktivitetIkkeMuligAGDTO(
                            ArbeidsrelatertArsakDTO(
                                "Trenger ståpult",
                                listOf(
                                    ArbeidsrelatertArsakTypeDTO
                                        .MANGLENDE_TILRETTELEGGING,
                                ),
                            ),
                        )
                    coEvery { mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1") } returns
                        (
                            createSykmeldtDbModel() to
                                createSykmeldingDbModel(
                                    sykmeldingId = sykmeldingId,
                                    sykmelding =
                                        createArbeidsgiverSykmelding(
                                            sykmeldingId = sykmeldingId,
                                            perioder =
                                                listOf(
                                                    createSykmeldingsperiode(
                                                        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                                                        aktivitetIkkeMulig = aktivitetIkkeMulig,
                                                    ),
                                                ),
                                        ),
                                )
                        )

                    val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
                    val periode: Periode? = result?.perioder?.first()

                    periode.shouldBeInstance<AktivitetIkkeMulig>()
                    periode.type shouldBeEqualTo PeriodeEnum.AKTIVITET_IKKE_MULIG
                    periode.arbeidsrelatertArsak.`should not be null`()
                    periode.arbeidsrelatertArsak?.arsak?.first() shouldBeEqualTo
                        ArbeidsrelatertArsakEnum.MANGLENDE_TILRETTELEGGING
                    periode.arbeidsrelatertArsak?.beskrivelse shouldBeEqualTo "Trenger ståpult"
                }

                test("should map to avventende") {
                    val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
                    coEvery { mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1") } returns
                        (
                            createSykmeldtDbModel() to
                                createSykmeldingDbModel(
                                    sykmeldingId = sykmeldingId,
                                    sykmelding =
                                        createArbeidsgiverSykmelding(
                                            sykmeldingId = sykmeldingId,
                                            perioder =
                                                listOf(
                                                    createSykmeldingsperiode(
                                                        type = PeriodetypeDTO.AVVENTENDE,
                                                        aktivitetIkkeMulig = null,
                                                        innspillTilArbeidsgiver = "Vi venter litt",
                                                    ),
                                                ),
                                        ),
                                )
                        )

                    val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
                    val periode: Periode? = result?.perioder?.first()

                    periode.shouldBeInstance<Avventende>()
                    periode.type shouldBeEqualTo PeriodeEnum.AVVENTENDE
                    periode.tilrettelegging shouldBeEqualTo "Vi venter litt"
                }

                test("should map to behandlingsdager") {
                    val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
                    coEvery { mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1") } returns
                        (
                            createSykmeldtDbModel() to
                                createSykmeldingDbModel(
                                    sykmeldingId = sykmeldingId,
                                    sykmelding =
                                        createArbeidsgiverSykmelding(
                                            sykmeldingId = sykmeldingId,
                                            perioder =
                                                listOf(
                                                    createSykmeldingsperiode(
                                                        type = PeriodetypeDTO.BEHANDLINGSDAGER,
                                                        aktivitetIkkeMulig = null,
                                                        behandlingsdager = 1,
                                                    ),
                                                ),
                                        ),
                                )
                        )

                    val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
                    val periode: Periode? = result?.perioder?.first()

                    periode.shouldBeInstance<Behandlingsdager>()
                    periode.type shouldBeEqualTo PeriodeEnum.BEHANDLINGSDAGER
                }

                test("should map to gradert") {
                    val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
                    coEvery { mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1") } returns
                        (
                            createSykmeldtDbModel() to
                                createSykmeldingDbModel(
                                    sykmeldingId = sykmeldingId,
                                    sykmelding =
                                        createArbeidsgiverSykmelding(
                                            sykmeldingId = sykmeldingId,
                                            perioder =
                                                listOf(
                                                    createSykmeldingsperiode(
                                                        type = PeriodetypeDTO.GRADERT,
                                                        aktivitetIkkeMulig = null,
                                                        gradert =
                                                            GradertDTO(
                                                                45,
                                                                true,
                                                            ),
                                                    ),
                                                ),
                                        ),
                                )
                        )

                    val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
                    val periode: Periode? = result?.perioder?.first()

                    periode.shouldBeInstance<Gradert>()
                    periode.type shouldBeEqualTo PeriodeEnum.GRADERT
                    periode.grad shouldBeEqualTo 45
                    periode.reisetilskudd shouldBeEqualTo true
                }

                test("should map to reisetilskudd") {
                    val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
                    coEvery { mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1") } returns
                        (
                            createSykmeldtDbModel() to
                                createSykmeldingDbModel(
                                    sykmeldingId = sykmeldingId,
                                    sykmelding =
                                        createArbeidsgiverSykmelding(
                                            sykmeldingId = sykmeldingId,
                                            perioder =
                                                listOf(
                                                    createSykmeldingsperiode(
                                                        type = PeriodetypeDTO.REISETILSKUDD,
                                                        aktivitetIkkeMulig = null,
                                                        reisetilskudd = true,
                                                    ),
                                                ),
                                        ),
                                )
                        )

                    val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
                    val periode: Periode? = result?.perioder?.first()

                    periode.shouldBeInstance<Reisetilskudd>()
                    periode.type shouldBeEqualTo PeriodeEnum.REISETILSKUDD
                }
            }

            context("getSoknad") {
                test("should map correct") {
                    val soknad = getFileAsString("src/test/resources/testSoknad.json")
                    val soknadDTO = objectMapper.readValue<Soknad>(soknad)
                    val soknadDbModel = soknadDTO.toSoknadDbModel()
                    val sykmeldtDbModel =
                        SykmeldtDbModel(
                            pasientFnr = "12345678912",
                            pasientNavn = "navn",
                            startdatoSykefravaer = LocalDate.now(),
                            latestTom = LocalDate.now(),
                            sistOppdatert = null,
                        )
                    coEvery { mineSykmeldteDb.getSoknad(soknadDbModel.soknadId, "red-2") } returns
                        (sykmeldtDbModel to soknadDbModel)

                    val result = mineSykmeldtService.getSoknad(soknadDbModel.soknadId, "red-2")
                    result shouldNotBe null
                }

                test("should map to Soknad") {
                    val soknadId = "e94a7c0f-3240-4a0c-8788-c4cc3ebcdac2"
                    coEvery { mineSykmeldteDb.getSoknad(soknadId, "red-2") } returns
                        (
                            createSykmeldtDbModel(
                                pasientNavn = "Navn Navnesen",
                            ) to
                                createSoknadDbModel(
                                    soknadId = soknadId,
                                    sykmeldingId = "31c5b5ca-1248-4280-bc2e-3c6b11c365b9",
                                    tom = LocalDate.parse("2021-04-04"),
                                    sendtDato = LocalDate.parse("2021-04-04"),
                                    timestamp = OffsetDateTime.parse("2021-11-18T14:06:12Z"),
                                    soknad =
                                        mockk<Soknad>().also {
                                            every { it.fom } returns LocalDate.parse("2021-10-01")
                                            every { it.korrigerer } returns null
                                            every { it.korrigertAv } returns
                                                "jd14jfqd-0422-4a5e-b779-a8819abf"
                                            every { it.soknadsperioder } returns
                                                listOf(
                                                    Soknadsperiode(
                                                        fom = LocalDate.parse("2021-10-04"),
                                                        tom = LocalDate.parse("2021-10-12"),
                                                        sykmeldingstype =
                                                            Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                                                        sykmeldingsgrad = null,
                                                    ),
                                                )
                                            every { it.sendtNav } returns
                                                LocalDateTime.parse("2022-05-09T08:56:24")
                                            every { it.sendtArbeidsgiver } returns
                                                LocalDateTime.parse("2022-05-10T08:56:24")
                                            every { it.sporsmal } returns
                                                listOf(
                                                    Sporsmal(
                                                        id = "54217564",
                                                        tag = "label",
                                                        min = "2021-10-03",
                                                        max = "2021-10-06",
                                                        sporsmalstekst = "Er dette et spørsmål?",
                                                        undertekst = "Undertekst til spørsmålet",
                                                        svartype = Svartype.FRITEKST,
                                                        kriterieForVisningAvUndersporsmal =
                                                            Visningskriterium.JA,
                                                        svar =
                                                            listOf(
                                                                no.nav.syfo.soknad.model.Svar(
                                                                    verdi = "Ja",
                                                                ),
                                                            ),
                                                        undersporsmal = emptyList(),
                                                    ),
                                                )
                                        },
                                )
                        )

                    val result = mineSykmeldtService.getSoknad(soknadId, "red-2")

                    result.shouldNotBeNull()
                    result.id shouldBeEqualTo soknadId
                    result.sykmeldingId shouldBeEqualTo "31c5b5ca-1248-4280-bc2e-3c6b11c365b9"
                    result.navn shouldBeEqualTo "Navn Navnesen"
                    result.korrigererSoknadId shouldBeEqualTo null
                    result.korrigertBySoknadId shouldBeEqualTo "jd14jfqd-0422-4a5e-b779-a8819abf"
                    result.perioder[0].fom shouldBeEqualTo LocalDate.parse("2021-10-04")
                    result.perioder[0].tom shouldBeEqualTo LocalDate.parse("2021-10-12")
                    result.perioder[0].sykmeldingstype shouldBeEqualTo
                        PeriodeEnum.AKTIVITET_IKKE_MULIG
                    result.sporsmal[0].tag shouldBeEqualTo "label"
                    result.sporsmal[0].min shouldBeEqualTo "2021-10-03"
                    result.sporsmal[0].max shouldBeEqualTo "2021-10-06"
                    result.sporsmal[0].svartype shouldBeEqualTo Svartype.FRITEKST
                    result.sporsmal[0].svar shouldBeEqualTo
                        listOf(
                            Svar(
                                verdi = "Ja",
                            ),
                        )
                }

                test(
                    "should filter out sporsmal tag BEKREFT_OPPLYSNINGER and VAER_KLAR_OVER_AT from Soknad",
                ) {
                    val soknadId = "b8aa4075-7347-48c9-b006-a770ee023bf8"
                    coEvery { mineSykmeldteDb.getSoknad(soknadId, "soknad-112") } returns
                        (
                            createSykmeldtDbModel(
                                pasientNavn = "Navn Navnesen",
                            ) to
                                createSoknadDbModel(
                                    soknadId = soknadId,
                                    soknad =
                                        mockk<Soknad>().also {
                                            every { it.fom } returns LocalDate.parse("2021-10-01")
                                            every { it.korrigerer } returns null
                                            every { it.korrigertAv } returns
                                                "jd14jfqd-0422-4a5e-b779-a8819abf"
                                            every { it.soknadsperioder } returns
                                                listOf(
                                                    Soknadsperiode(
                                                        fom = LocalDate.parse("2021-10-04"),
                                                        tom = LocalDate.parse("2021-10-12"),
                                                        sykmeldingstype =
                                                            Sykmeldingstype.AKTIVITET_IKKE_MULIG,
                                                        sykmeldingsgrad = null,
                                                    ),
                                                )
                                            every { it.sendtNav } returns
                                                LocalDateTime.parse("2022-05-09T08:56:24")
                                            every { it.sendtArbeidsgiver } returns
                                                LocalDateTime.parse("2022-05-10T08:56:24")
                                            every { it.sporsmal } returns
                                                listOf(
                                                    Sporsmal(
                                                        id = "54217564",
                                                        tag = "BEKREFT_OPPLYSNINGER",
                                                        min = "2024-03-03",
                                                        max = "2024-03-06",
                                                        sporsmalstekst =
                                                            "Jeg har lest all informasjonen jeg har fått i søknaden og bekrefter at opplysningene jeg har gitt er korrekte.",
                                                        undertekst = "Undertekst til spørsmålet",
                                                        svartype = Svartype.CHECKBOX_PANEL,
                                                        kriterieForVisningAvUndersporsmal = null,
                                                        svar =
                                                            listOf(
                                                                no.nav.syfo.soknad.model.Svar(
                                                                    verdi = "CHECKED",
                                                                ),
                                                            ),
                                                        undersporsmal = emptyList(),
                                                    ),
                                                    Sporsmal(
                                                        id = "54217564",
                                                        tag = "VAER_KLAR_OVER_AT",
                                                        min = "2024-03-03",
                                                        max = "2024-03-06",
                                                        sporsmalstekst = "Viktig å være klar over:",
                                                        undertekst = "Undertekst til spørsmålet",
                                                        svartype = Svartype.IKKE_RELEVANT,
                                                        kriterieForVisningAvUndersporsmal = null,
                                                        svar =
                                                            listOf(
                                                                no.nav.syfo.soknad.model.Svar(
                                                                    verdi = "CHECKED",
                                                                ),
                                                            ),
                                                        undersporsmal = emptyList(),
                                                    ),
                                                    Sporsmal(
                                                        id = "54217564",
                                                        tag = "HVOR_MYE_HAR_DU_JOBBET",
                                                        min = "2024-03-03",
                                                        max = "2024-03-06",
                                                        sporsmalstekst =
                                                            "Hvor mye jobbet du totalt 20. mai - 5. juni 2020 hos 0102983875 sitt orgnavn?",
                                                        undertekst = "Undertekst til spørsmålet",
                                                        svartype =
                                                            Svartype.RADIO_GRUPPE_TIMER_PROSENT,
                                                        kriterieForVisningAvUndersporsmal = null,
                                                        svar =
                                                            listOf(
                                                                no.nav.syfo.soknad.model.Svar(
                                                                    verdi = "Ja",
                                                                ),
                                                            ),
                                                        undersporsmal = emptyList(),
                                                    ),
                                                )
                                        },
                                )
                        )

                    val result = mineSykmeldtService.getSoknad(soknadId, "soknad-112")

                    result.shouldNotBeNull()
                    result.id shouldBeEqualTo "b8aa4075-7347-48c9-b006-a770ee023bf8"
                    result.navn shouldBeEqualTo "Navn Navnesen"
                    result.sporsmal.size shouldBeEqualTo 1
                    result.sporsmal[0].tag shouldBeEqualTo "HVOR_MYE_HAR_DU_JOBBET"
                }
            }
        },
    )

private fun createSoknadDbModel(
    soknadId: String = "0007c3f0-c401-4124-a7f1-a2faf5fd0ac8",
    sykmeldingId: String = "31c5b5ca-1248-4280-bc2e-3c6b11c365b9",
    pasientFnr: String = "09099012345",
    orgnummer: String = "0102983875",
    soknad: Soknad =
        mockk<Soknad>(relaxed = true).also {
            every { it.status } returns NY
            every { it.fom } returns LocalDate.now()
        },
    sendtDato: LocalDate = LocalDate.now(),
    tom: LocalDate = LocalDate.now(),
    lest: Boolean = false,
    timestamp: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
): SoknadDbModel =
    SoknadDbModel(
        soknadId = soknadId,
        sykmeldingId = sykmeldingId,
        pasientFnr = pasientFnr,
        orgnummer = orgnummer,
        sykepengesoknad = soknad,
        sendtDato = sendtDato,
        tom = tom,
        lest = lest,
        timestamp = timestamp,
    )

private fun createSykmeldingDbModel(
    sykmeldingId: String = "c4df78c6-880a-4a47-bc4f-9df63584c009",
    pasientFnr: String = "08088012345",
    orgnummer: String = "90909012345",
    orgnavn: String = "Baker Frank",
    sykmelding: ArbeidsgiverSykmelding = createArbeidsgiverSykmelding(sykmeldingId),
    lest: Boolean = false,
    timestamp: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    latestTom: LocalDate = LocalDate.now(),
    sendtTilArbeidsgiverDato: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
) = SykmeldingDbModel(
    sykmeldingId = sykmeldingId,
    pasientFnr = pasientFnr,
    orgnummer = orgnummer,
    orgnavn = orgnavn,
    sykmelding = sykmelding,
    lest = lest,
    timestamp = timestamp,
    latestTom = latestTom,
    sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
    egenmeldingsdager = null,
)

private fun createSykmeldtDbModel(
    pasientFnr: String = "08088012345",
    pasientNavn: String = "Ola Normann",
    startdatoSykefravaer: LocalDate = LocalDate.now(),
    latestTom: LocalDate = LocalDate.now(),
) = SykmeldtDbModel(
    pasientFnr = pasientFnr,
    pasientNavn = pasientNavn,
    startdatoSykefravaer = startdatoSykefravaer,
    latestTom = latestTom,
    sistOppdatert = null,
)

fun getSykmeldtData(
    sykmeldte: Int,
    sykmeldinger: List<ArbeidsgiverSykmelding> =
        listOf(
            createArbeidsgiverSykmelding(
                UUID.randomUUID().toString(),
            ),
        ),
    soknader: Int = 0,
    sykmeldtFnrPrefix: String = "prefix",
): List<MinSykmeldtDbModel> =
    (0 until sykmeldte).flatMap {
        val sykmeldtFnr = "$sykmeldtFnrPrefix-$it"
        val narmestelederId = UUID.randomUUID().toString()
        val orgnummer = "orgnummer"
        val sykmeldtNavn = "Navn"
        val startDatoSykefravar = LocalDate.now()
        val orgnavn = "orgnavn"
        sykmeldinger.mapIndexed { index, arbeigsgiverSykmelding ->
            MinSykmeldtDbModel(
                sykmeldtFnr = sykmeldtFnr,
                narmestelederId = narmestelederId,
                orgnummer = orgnummer,
                sykmeldtNavn = sykmeldtNavn,
                startDatoSykefravar = startDatoSykefravar,
                sykmeldingId = arbeigsgiverSykmelding.id,
                orgNavn = orgnavn,
                sykmelding = arbeigsgiverSykmelding,
                soknad =
                    if (soknader != 0 && index < soknader) {
                        createSoknad(
                            soknadId = UUID.randomUUID().toString(),
                            sykmeldingId = arbeigsgiverSykmelding.id,
                        )
                    } else {
                        null
                    },
                lestSoknad = false,
                lestSykmelding = false,
                sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC),
                egenmeldingsdager = null,
            )
        }
    }

fun getFileAsString(filePath: String) =
    String(
        Files.readAllBytes(
            Paths.get(filePath),
        ),
        StandardCharsets.UTF_8,
    )
