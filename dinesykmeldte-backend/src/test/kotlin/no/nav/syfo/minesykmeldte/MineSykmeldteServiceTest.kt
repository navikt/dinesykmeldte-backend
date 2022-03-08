package no.nav.syfo.minesykmeldte

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SporsmalDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SvarDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SvartypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykmeldingstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.VisningskriteriumDTO
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
import no.nav.syfo.minesykmeldte.model.PreviewKorrigertSoknad
import no.nav.syfo.minesykmeldte.model.PreviewNySoknad
import no.nav.syfo.minesykmeldte.model.PreviewSendtSoknad
import no.nav.syfo.minesykmeldte.model.Reisetilskudd
import no.nav.syfo.minesykmeldte.model.Svar
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.model.ArbeidsrelatertArsakDTO
import no.nav.syfo.model.sykmelding.model.ArbeidsrelatertArsakTypeDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.createArbeidsgiverSykmelding
import no.nav.syfo.util.createSykepengesoknadDto
import no.nav.syfo.util.createSykmeldingsperiode
import no.nav.syfo.util.shouldBeInstance
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class MineSykmeldteServiceTest : Spek({
    val mineSykmeldteDb = mockk<MineSykmeldteDb>(relaxed = true)
    val mineSykmeldtService = MineSykmeldteService(mineSykmeldteDb)

    afterEachTest {
        clearMocks(mineSykmeldteDb)
    }

    describe("getMineSykmeldte") {

        it("Should get mine sykmeldte with hendelser") {
            every { mineSykmeldteDb.getHendelser("1") } returns listOf(
                HendelseDbModel(
                    id = "12",
                    pasientFnr = "avdeling-1-0",
                    orgnummer = "orgnummer",
                    oppgavetype = "DIALOGMOTE_INNKALLING",
                    lenke = "localhost",
                    tekst = "Innkalling til dialogmøte",
                    timestamp = OffsetDateTime.now(),
                    utlopstidspunkt = null,
                    ferdigstilt = false,
                    ferdigstiltTimestamp = null,
                    hendelseId = UUID.randomUUID()
                )
            )
            every { mineSykmeldteDb.getMineSykmeldte("1") } returns
                getSykmeldtData(
                    sykmeldte = 2,
                    sykmeldinger = listOf(
                        createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                        createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                    ),
                    sykmeldtFnrPrefix = "avdeling-1",
                    soknader = 1
                )
            runBlocking {
                val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                mineSykmeldte shouldHaveSize 2
                mineSykmeldte.first { it.fnr == "avdeling-1-0" }.dialogmoter shouldHaveSize 1
                mineSykmeldte.first { it.fnr == "avdeling-1-1" }.dialogmoter shouldHaveSize 0
            }
        }

        it("Should get empty list") {
            every { mineSykmeldteDb.getMineSykmeldte("1") } returns emptyList()
            every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
            runBlocking {
                mineSykmeldtService.getMineSykmeldte("1").size shouldBeEqualTo 0
            }
        }

        it("should get one sykmeldt") {
            every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(1, sykmeldtFnrPrefix = "prefix")
            every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
            runBlocking {
                val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "100%"
            }
        }

        it("Should get one sykmeldt with one IKKE_SENDT_SOKNAD") {
            val sykmeldtData = getSykmeldtData(1, sykmeldtFnrPrefix = "prefix", soknader = 1)
                .map { it.copy(soknad = it.soknad!!.copy(status = SoknadsstatusDTO.NY)) }

            every { mineSykmeldteDb.getHendelser("1") } returns listOf(
                HendelseDbModel(
                    id = sykmeldtData.first().soknad!!.id,
                    pasientFnr = "prefix-0",
                    orgnummer = "orgnummer",
                    oppgavetype = "IKKE_SENDT_SOKNAD",
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(),
                    utlopstidspunkt = null,
                    ferdigstilt = false,
                    ferdigstiltTimestamp = null,
                    hendelseId = UUID.randomUUID()
                )
            )

            every { mineSykmeldteDb.getMineSykmeldte("1") } returns sykmeldtData
            runBlocking {
                val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "100%"
                (mineSykmeldte.first().previewSoknader.first() as PreviewNySoknad).ikkeSendtSoknadVarsel shouldBeEqualTo true
            }
        }

        it("should group sykmeldinger and søknader by sykmeldt") {
            every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
            every { mineSykmeldteDb.getMineSykmeldte("1") } returns
                getSykmeldtData(
                    sykmeldte = 3,
                    sykmeldinger = listOf(
                        createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                        createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                    ),
                    sykmeldtFnrPrefix = "avdeling-1",
                    soknader = 1
                ) +
                    getSykmeldtData(
                        sykmeldte = 2,
                        sykmeldinger = listOf(
                            createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                            createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                            createArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                        ),
                        sykmeldtFnrPrefix = "avdeling-2",
                        soknader = 0
                    )

            runBlocking {
                val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                mineSykmeldte shouldHaveSize 5
            }
        }

        describe("sykmeldt") {
            it("should not be friskmeldt if the latest sykmeldt period is less than 16 days ago") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        createArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                createSykmeldingsperiode(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(24),
                                    tom = LocalDate.now().minusDays(16),
                                    type = PeriodetypeDTO.GRADERT,
                                    gradert = GradertDTO(50, false),
                                )
                            )
                        )
                    ),
                    sykmeldtFnrPrefix = "prefix"
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte shouldHaveSize 1
                    mineSykmeldte.first().friskmeldt shouldBe false
                }
            }

            it("should be friskmeldt if the latest sykmeldt period is more than 16 days ago") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        createArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                createSykmeldingsperiode(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(24),
                                    tom = LocalDate.now().minusDays(17),
                                    type = PeriodetypeDTO.GRADERT,
                                    gradert = GradertDTO(50, false),
                                )
                            )
                        )
                    ),
                    sykmeldtFnrPrefix = "prefix"
                )

                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte shouldHaveSize 1
                    mineSykmeldte.first().friskmeldt shouldBe true
                }
            }
        }

        describe("given different types") {
            it("should get one sykmeldt with 50% type") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                    sykmeldtFnrPrefix = "prefix"
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "50%"
                }
            }

            it("should get one sykmeldt with 20% type") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                    sykmeldtFnrPrefix = "prefix"
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "20%"
                }
            }

            it("should get one sykmeldt with avventende") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                    sykmeldtFnrPrefix = "prefix"
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Avventende"
                }
            }

            it("should get one sykmeldt with behandlingsdager") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                    sykmeldtFnrPrefix = "prefix"
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Behandlingsdager"
                }
            }

            it("should get one sykmeldt with reisetilskudd") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                    sykmeldtFnrPrefix = "prefix"
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
                }
            }

            it("should pick the correct period when one period is now") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
                }
            }

            it("should pick the correct period when now is end of period") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
                }
            }

            it("should pick the correct period when now is start of period") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
                }
            }

            it("should pick latest period when there are periods in the past, but one in the future") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
                }
            }

            it("should pick the nearest future period, if all in the future") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(
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
                                )
                            )
                        )
                    ),
                )
                runBlocking {
                    val mineSykmeldte = mineSykmeldtService.getMineSykmeldte("1")
                    mineSykmeldte.size shouldBeEqualTo 1
                    mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
                }
            }
        }

        describe("when mapping søknader") {
            it("should map to a new søknad and use tom if date is latest") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                val soknad = createSykepengesoknadDto("soknad-id", "sykmeldingId").copy(
                    status = SoknadsstatusDTO.NY,
                    tom = LocalDate.parse("2020-05-02"),
                    opprettet = LocalDateTime.parse("2020-04-05T18:00:50.63"),
                )

                every { mineSykmeldteDb.getMineSykmeldte("1") } returns listOf(
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
                    )
                )
                runBlocking {
                    val mineSykeldte = mineSykmeldtService.getMineSykmeldte("1")
                    val mappedSoknad = mineSykeldte[0].previewSoknader[0]

                    mappedSoknad.shouldBeInstance<PreviewNySoknad>()
                    mappedSoknad.varsel shouldBeEqualTo true
                }
            }

            it("should map to a new søknad and use opprettet if date is latest") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                val soknad = createSykepengesoknadDto("soknad-id", "sykmeldingId").copy(
                    status = SoknadsstatusDTO.NY,
                    tom = LocalDate.parse("2020-05-02"),
                    opprettet = LocalDateTime.parse("2020-06-05T18:00:50.63"),
                )

                every { mineSykmeldteDb.getMineSykmeldte("1") } returns listOf(
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
                    )
                )
                runBlocking {
                    val mineSykeldte = mineSykmeldtService.getMineSykmeldte("1")
                    val mappedSoknad = mineSykeldte[0].previewSoknader[0]

                    mappedSoknad.shouldBeInstance<PreviewNySoknad>()
                    mappedSoknad.varsel shouldBeEqualTo false
                }
            }

            it("should map to a sendt søknad") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                val soknad = createSykepengesoknadDto("soknad-id", "sykmeldingId").copy(
                    status = SoknadsstatusDTO.SENDT,
                    korrigertAv = "korrigert-av-id",
                    sendtArbeidsgiver = LocalDateTime.parse("2020-06-07T19:34:50.63")
                )

                every { mineSykmeldteDb.getMineSykmeldte("1") } returns listOf(
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
                    )
                )
                runBlocking {
                    val mineSykeldte = mineSykmeldtService.getMineSykmeldte("1")
                    val mappedSoknad = mineSykeldte[0].previewSoknader[0]

                    mappedSoknad.shouldBeInstance<PreviewSendtSoknad>()
                    mappedSoknad.lest shouldBeEqualTo true
                    mappedSoknad.korrigertBySoknadId shouldBeEqualTo "korrigert-av-id"
                    mappedSoknad.sendtDato shouldBeEqualTo LocalDateTime.parse("2020-06-07T19:34:50.63")
                }
            }

            it("should map to a fremtidig søknad") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                val soknad = createSykepengesoknadDto("soknad-id", "sykmeldingId").copy(
                    status = SoknadsstatusDTO.FREMTIDIG,
                )

                every { mineSykmeldteDb.getMineSykmeldte("1") } returns listOf(
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
                    )
                )
                runBlocking {
                    val mineSykeldte = mineSykmeldtService.getMineSykmeldte("1")
                    val mappedSoknad = mineSykeldte[0].previewSoknader[0]

                    mappedSoknad.shouldBeInstance<PreviewFremtidigSoknad>()
                }
            }

            it("should map to a korrigert søknad") {
                every { mineSykmeldteDb.getHendelser("1") } returns emptyList()
                val soknad = createSykepengesoknadDto("soknad-id", "sykmeldingId").copy(
                    status = SoknadsstatusDTO.KORRIGERT,
                    korrigerer = "korrigerer",
                    korrigertAv = "korrigert-av"
                )

                every { mineSykmeldteDb.getMineSykmeldte("1") } returns listOf(
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
                    )
                )
                runBlocking {
                    val mineSykeldte = mineSykmeldtService.getMineSykmeldte("1")
                    val mappedSoknad = mineSykeldte[0].previewSoknader[0]

                    mappedSoknad.shouldBeInstance<PreviewKorrigertSoknad>()
                    mappedSoknad.korrigertBySoknadId shouldBeEqualTo "korrigert-av"
                    mappedSoknad.korrigererSoknadId shouldBeEqualTo "korrigerer"
                }
            }
        }
    }

    describe("getSykmelding") {
        it("should map to aktivitetIkkeMulig") {
            val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                createSykmeldtDbModel() to createSykmeldingDbModel(
                    sykmeldingId = sykmeldingId,
                    sykmelding = createArbeidsgiverSykmelding(
                        sykmeldingId = sykmeldingId,
                        perioder = listOf(
                            createSykmeldingsperiode(
                                type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                                aktivitetIkkeMulig = AktivitetIkkeMuligAGDTO(
                                    ArbeidsrelatertArsakDTO(
                                        "Trenger ståpult",
                                        listOf(ArbeidsrelatertArsakTypeDTO.MANGLENDE_TILRETTELEGGING)
                                    )
                                )
                            )
                        )
                    )
                )
                )

            val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<AktivitetIkkeMulig>()
            periode.type shouldBeEqualTo PeriodeEnum.AKTIVITET_IKKE_MULIG
            periode.arbeidsrelatertArsak.`should not be null`()
            periode.arbeidsrelatertArsak?.arsak?.first() shouldBeEqualTo ArbeidsrelatertArsakEnum.MANGLENDE_TILRETTELEGGING
            periode.arbeidsrelatertArsak?.beskrivelse shouldBeEqualTo "Trenger ståpult"
        }

        it("should map to avventende") {
            val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                createSykmeldtDbModel() to createSykmeldingDbModel(
                    sykmeldingId = sykmeldingId,
                    sykmelding = createArbeidsgiverSykmelding(
                        sykmeldingId = sykmeldingId,
                        perioder = listOf(
                            createSykmeldingsperiode(
                                type = PeriodetypeDTO.AVVENTENDE,
                                aktivitetIkkeMulig = null,
                                innspillTilArbeidsgiver = "Vi venter litt"
                            )
                        )
                    )
                )
                )

            val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<Avventende>()
            periode.type shouldBeEqualTo PeriodeEnum.AVVENTENDE
            periode.tilrettelegging shouldBeEqualTo "Vi venter litt"
        }

        it("should map to behandlingsdager") {
            val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                createSykmeldtDbModel() to createSykmeldingDbModel(
                    sykmeldingId = sykmeldingId,
                    sykmelding = createArbeidsgiverSykmelding(
                        sykmeldingId = sykmeldingId,
                        perioder = listOf(
                            createSykmeldingsperiode(
                                type = PeriodetypeDTO.BEHANDLINGSDAGER,
                                aktivitetIkkeMulig = null,
                                behandlingsdager = 1
                            )
                        )
                    )
                )
                )

            val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<Behandlingsdager>()
            periode.type shouldBeEqualTo PeriodeEnum.BEHANDLINGSDAGER
        }

        it("should map to gradert") {
            val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                createSykmeldtDbModel() to createSykmeldingDbModel(
                    sykmeldingId = sykmeldingId,
                    sykmelding = createArbeidsgiverSykmelding(
                        sykmeldingId = sykmeldingId,
                        perioder = listOf(
                            createSykmeldingsperiode(
                                type = PeriodetypeDTO.GRADERT,
                                aktivitetIkkeMulig = null,
                                gradert = GradertDTO(
                                    45, true
                                ),
                            )
                        )
                    )
                )
                )

            val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<Gradert>()
            periode.type shouldBeEqualTo PeriodeEnum.GRADERT
            periode.grad shouldBeEqualTo 45
            periode.reisetilskudd shouldBeEqualTo true
        }

        it("should map to reisetilskudd") {
            val sykmeldingId = "c4df78c6-880a-4a47-bc4f-9df63584c009"
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                createSykmeldtDbModel() to createSykmeldingDbModel(
                    sykmeldingId = sykmeldingId,
                    sykmelding = createArbeidsgiverSykmelding(
                        sykmeldingId = sykmeldingId,
                        perioder = listOf(
                            createSykmeldingsperiode(
                                type = PeriodetypeDTO.REISETILSKUDD,
                                aktivitetIkkeMulig = null,
                                reisetilskudd = true
                            )
                        )
                    )
                )
                )

            val result = mineSykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<Reisetilskudd>()
            periode.type shouldBeEqualTo PeriodeEnum.REISETILSKUDD
        }
    }

    describe("getSoknad") {
        it("should map to Soknad") {
            val soknadId = "e94a7c0f-3240-4a0c-8788-c4cc3ebcdac2"
            every {
                mineSykmeldteDb.getSoknad(soknadId, "red-2")
            } returns (
                createSykmeldtDbModel(
                    pasientNavn = "Navn Navnesen"
                ) to createSoknadDbModel(
                    soknadId = soknadId,
                    sykmeldingId = "31c5b5ca-1248-4280-bc2e-3c6b11c365b9",
                    tom = LocalDate.parse("2021-04-04"),
                    sendtDato = LocalDate.parse("2021-04-04"),
                    timestamp = OffsetDateTime.parse("2021-11-18T14:06:12Z"),
                    soknad = mockk<SykepengesoknadDTO>().also {
                        every { it.fom } returns LocalDate.parse("2021-10-01")
                        every { it.korrigerer } returns null
                        every { it.korrigertAv } returns "jd14jfqd-0422-4a5e-b779-a8819abf"
                        every { it.soknadsperioder } returns listOf(
                            SoknadsperiodeDTO(
                                fom = LocalDate.parse("2021-10-04"),
                                tom = LocalDate.parse("2021-10-12"),
                                sykmeldingstype = SykmeldingstypeDTO.AKTIVITET_IKKE_MULIG,
                            )
                        )
                        every { it.sporsmal } returns listOf(
                            SporsmalDTO(
                                id = "54217564",
                                tag = "label",
                                min = "2021-10-03",
                                max = "2021-10-06",
                                sporsmalstekst = "Er dette et spørsmål?",
                                undertekst = "Undertekst til spørsmålet",
                                svartype = SvartypeDTO.FRITEKST,
                                kriterieForVisningAvUndersporsmal = VisningskriteriumDTO.JA,
                                svar = listOf(
                                    SvarDTO(
                                        verdi = "Ja",
                                    )
                                ),
                            )
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
            result.perioder[0].sykmeldingstype shouldBeEqualTo PeriodeEnum.AKTIVITET_IKKE_MULIG
            result.sporsmal[0].tag shouldBeEqualTo "label"
            result.sporsmal[0].min shouldBeEqualTo "2021-10-03"
            result.sporsmal[0].max shouldBeEqualTo "2021-10-06"
            result.sporsmal[0].svartype shouldBeEqualTo SvartypeDTO.FRITEKST
            result.sporsmal[0].svar shouldBeEqualTo listOf(
                Svar(
                    verdi = "Ja",
                )
            )
        }
    }
})

private fun createSoknadDbModel(
    soknadId: String = "0007c3f0-c401-4124-a7f1-a2faf5fd0ac8",
    sykmeldingId: String = "31c5b5ca-1248-4280-bc2e-3c6b11c365b9",
    pasientFnr: String = "09099012345",
    orgnummer: String = "0102983875",
    soknad: SykepengesoknadDTO = mockk<SykepengesoknadDTO>(relaxed = true).also {
        every { it.type } returns SoknadstypeDTO.ARBEIDSLEDIG
        every { it.status } returns SoknadsstatusDTO.NY
        every { it.fom } returns LocalDate.now()
    },
    sendtDato: LocalDate = LocalDate.now(),
    tom: LocalDate = LocalDate.now(),
    lest: Boolean = false,
    timestamp: OffsetDateTime = OffsetDateTime.now(),
): SoknadDbModel = SoknadDbModel(
    soknadId = soknadId,
    sykmeldingId = sykmeldingId,
    pasientFnr = pasientFnr,
    orgnummer = orgnummer,
    soknad = soknad,
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
    timestamp: OffsetDateTime = OffsetDateTime.now(),
    latestTom: LocalDate = LocalDate.now(),
) = SykmeldingDbModel(
    sykmeldingId = sykmeldingId,
    pasientFnr = pasientFnr,
    orgnummer = orgnummer,
    orgnavn = orgnavn,
    sykmelding = sykmelding,
    lest = lest,
    timestamp = timestamp,
    latestTom = latestTom,
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
)

fun getSykmeldtData(
    sykmeldte: Int,
    sykmeldinger: List<ArbeidsgiverSykmelding> = listOf(
        createArbeidsgiverSykmelding(
            UUID.randomUUID().toString(),
        )
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
                soknad = if (soknader != 0 && index < soknader) createSykepengesoknadDto(
                    soknadId = UUID.randomUUID().toString(),
                    sykmeldingId = arbeigsgiverSykmelding.id
                ) else null,
                lestSoknad = false,
                lestSykmelding = false,
            )
        }
    }
