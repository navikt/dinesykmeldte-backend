package no.nav.syfo.minesykmeldte

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.kafka.felles.SoknadsstatusDTO
import no.nav.syfo.kafka.felles.SoknadstypeDTO
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.minesykmeldte.db.MinSykmeldtDbModel
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.db.getSykepengesoknadDto
import no.nav.syfo.minesykmeldte.model.AktivitetIkkeMulig
import no.nav.syfo.minesykmeldte.model.ArbeidsrelatertArsakEnum
import no.nav.syfo.minesykmeldte.model.Avventende
import no.nav.syfo.minesykmeldte.model.Behandlingsdager
import no.nav.syfo.minesykmeldte.model.Gradert
import no.nav.syfo.minesykmeldte.model.Periode
import no.nav.syfo.minesykmeldte.model.PeriodeEnum
import no.nav.syfo.minesykmeldte.model.Reisetilskudd
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
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class MineSykmeldteServiceTest : Spek({
    val mineSykmeldteDb = mockk<MineSykmeldteDb>()
    val minesykmeldtService = MineSykmeldteService(mineSykmeldteDb)

    afterEachTest {
        clearMocks(mineSykmeldteDb)
    }

    describe("getMineSykmeldte") {
        it("Should get empty list") {
            every { mineSykmeldteDb.getMineSykmeldte("1") } returns emptyList()
            minesykmeldtService.getMineSykmeldte("1").size shouldBeEqualTo 0
        }

        it("should get one sykmeldt") {
            every { mineSykmeldteDb.getMineSykmeldte("1") } returns getSykmeldtData(1, sykmeldtFnrPrefix = "prefix")
            val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
            mineSykmeldte.size shouldBeEqualTo 1
            mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "100%"
        }

        it("should group sykmeldinger and søknader by sykmeldt") {
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

            val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")

            mineSykmeldte shouldHaveSize 5
        }

        describe("sykmeldt") {
            it("should not be friskmeldt if the latest sykmeldt period is less than 16 days ago") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte shouldHaveSize 1
                mineSykmeldte.first().friskmeldt shouldBe false
            }

            it("should be friskmeldt if the latest sykmeldt period is more than 16 days ago") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte shouldHaveSize 1
                mineSykmeldte.first().friskmeldt shouldBe true
            }
        }

        describe("given different types") {
            it("should get one sykmeldt with 50% type") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "50%"
            }

            it("should get one sykmeldt with 20% type") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "20%"
            }

            it("should get one sykmeldt with avventende") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Avventende"
            }

            it("should get one sykmeldt with behandlingsdager") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Behandlingsdager"
            }

            it("should get one sykmeldt with reisetilskudd") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
            }

            it("should pick the correct period when one period is now") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
            }

            it("should pick the correct period when now is end of period") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
            }

            it("should pick the correct period when now is start of period") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
            }

            it("should pick latest period when there are periods in the past, but one in the future") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
            }

            it("should pick the nearest future period, if all in the future") {
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

                val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
                mineSykmeldte.size shouldBeEqualTo 1
                mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "Reisetilskudd"
            }
        }
    }

    describe("getSykmelding") {
        it("should map to aktivitetIkkeMulig") {
            val sykmeldingId = UUID.fromString("c4df78c6-880a-4a47-bc4f-9df63584c009")
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                    createSykmeldtDbModel() to createSykmeldingDbModel(
                        sykmeldingId = sykmeldingId,
                        sykmelding = createArbeidsgiverSykmelding(
                            sykmeldingId = sykmeldingId.toString(),
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

            val result = minesykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<AktivitetIkkeMulig>()
            periode.type shouldBeEqualTo PeriodeEnum.AKTIVITET_IKKE_MULIG
            periode.arbeidsrelatertArsak.`should not be null`()
            periode.arbeidsrelatertArsak?.arsak?.first() shouldBeEqualTo ArbeidsrelatertArsakEnum.MANGLENDE_TILRETTELEGGING
            periode.arbeidsrelatertArsak?.beskrivelse shouldBeEqualTo "Trenger ståpult"
        }

        it("should map to avventende") {
            val sykmeldingId = UUID.fromString("c4df78c6-880a-4a47-bc4f-9df63584c009")
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                    createSykmeldtDbModel() to createSykmeldingDbModel(
                        sykmeldingId = sykmeldingId,
                        sykmelding = createArbeidsgiverSykmelding(
                            sykmeldingId = sykmeldingId.toString(),
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

            val result = minesykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<Avventende>()
            periode.type shouldBeEqualTo PeriodeEnum.AVVENTENDE
            periode.tilrettelegging shouldBeEqualTo "Vi venter litt"
        }

        it("should map to behandlingsdager") {
            val sykmeldingId = UUID.fromString("c4df78c6-880a-4a47-bc4f-9df63584c009")
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                    createSykmeldtDbModel() to createSykmeldingDbModel(
                        sykmeldingId = sykmeldingId,
                        sykmelding = createArbeidsgiverSykmelding(
                            sykmeldingId = sykmeldingId.toString(),
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

            val result = minesykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<Behandlingsdager>()
            periode.type shouldBeEqualTo PeriodeEnum.BEHANDLINGSDAGER
        }

        it("should map to gradert") {
            val sykmeldingId = UUID.fromString("c4df78c6-880a-4a47-bc4f-9df63584c009")
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                    createSykmeldtDbModel() to createSykmeldingDbModel(
                        sykmeldingId = sykmeldingId,
                        sykmelding = createArbeidsgiverSykmelding(
                            sykmeldingId = sykmeldingId.toString(),
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

            val result = minesykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<Gradert>()
            periode.type shouldBeEqualTo PeriodeEnum.GRADERT
            periode.grad shouldBeEqualTo 45
            periode.reisetilskudd shouldBeEqualTo true
        }

        it("should map to reisetilskudd") {
            val sykmeldingId = UUID.fromString("c4df78c6-880a-4a47-bc4f-9df63584c009")
            every {
                mineSykmeldteDb.getSykmelding(sykmeldingId, "red-1")
            } returns (
                    createSykmeldtDbModel() to createSykmeldingDbModel(
                        sykmeldingId = sykmeldingId,
                        sykmelding = createArbeidsgiverSykmelding(
                            sykmeldingId = sykmeldingId.toString(),
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

            val result = minesykmeldtService.getSykmelding(sykmeldingId, "red-1")
            val periode: Periode? = result?.perioder?.first()

            periode.shouldBeInstance<Reisetilskudd>()
            periode.type shouldBeEqualTo PeriodeEnum.REISETILSKUDD
        }
    }

    describe("getSoknad") {
        it("should map to Soknad") {
            val soknadId = UUID.fromString("e94a7c0f-3240-4a0c-8788-c4cc3ebcdac2")
            every {
                mineSykmeldteDb.getSoknad(soknadId, "red-2")
            } returns (createSykmeldtDbModel() to createSoknadDbModel(
                soknadId = soknadId.toString(),
                sykmeldingId = "31c5b5ca-1248-4280-bc2e-3c6b11c365b9",
                tom = LocalDate.parse("2021-04-04"),
                sendtDato = LocalDate.parse("2021-04-04"),
                timestamp = OffsetDateTime.parse("2021-11-18T14:06:12Z"),
            ))

            val result = minesykmeldtService.getSoknad(soknadId, "red-2")

            result.shouldNotBeNull()
            result.soknadId shouldBeEqualTo soknadId
            result.details.type shouldBeEqualTo SoknadstypeDTO.ARBEIDSLEDIG
            result.details.status shouldBeEqualTo SoknadsstatusDTO.NY
        }
    }
})

private fun createSoknadDbModel(
    soknadId: String = "0007c3f0-c401-4124-a7f1-a2faf5fd0ac8",
    sykmeldingId: String = "31c5b5ca-1248-4280-bc2e-3c6b11c365b9",
    pasientFnr: String = "09099012345",
    orgnummer: String = "0102983875",
    soknad: SykepengesoknadDTO = mockk<SykepengesoknadDTO>().also {
        every { it.type } returns SoknadstypeDTO.ARBEIDSLEDIG
        every { it.status } returns SoknadsstatusDTO.NY
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
    sykmeldingId: UUID = UUID.fromString("c4df78c6-880a-4a47-bc4f-9df63584c009"),
    pasientFnr: String = "08088012345",
    orgnummer: String = "90909012345",
    orgnavn: String = "Baker Frank",
    sykmelding: ArbeidsgiverSykmelding = createArbeidsgiverSykmelding(sykmeldingId.toString()),
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
    sykmeldtFnrPrefix: String = "prefix"
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
                sykmeldingId = UUID.fromString(arbeigsgiverSykmelding.id),
                orgNavn = orgnavn,
                sykmelding = arbeigsgiverSykmelding,
                soknad = if (soknader != 0 && index < soknader) getSykepengesoknadDto(
                    UUID.randomUUID().toString(),
                    UUID.fromString(arbeigsgiverSykmelding.id),
                ) else null,
                lestSoknad = false,
                lestSykmelding = false,
            )
        }
    }
