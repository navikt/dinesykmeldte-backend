package no.nav.syfo.minesykmeldte

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.db.SykmeldtDbModel
import no.nav.syfo.minesykmeldte.db.getSykepengesoknadDto
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.getArbeidsgiverSykmelding
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.UUID

class MineSykmeldteServiceTest : Spek({
    val db = mockk<MineSykmeldteDb>()
    val minesykmeldtService = MineSykmeldteService(db)

    afterEachTest {
        clearMocks(db)
    }

    describe("Test minesykmeldteservice") {
        it("Should get empty list") {
            every { db.getMineSykmeldte("1") } returns emptyList()
            minesykmeldtService.getMineSykmeldte("1").size shouldBeEqualTo 0
        }

        it("should get one sykmeldt") {
            every { db.getMineSykmeldte("1") } returns getSykmeldtData(1, sykmeldtFnrPrefix = "prefix")
            val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
            mineSykmeldte.size shouldBeEqualTo 1
            mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "100%"
        }

        it("should group sykmeldinger and søknader by sykmeldt") {
            every { db.getMineSykmeldte("1") } returns
                getSykmeldtData(
                    sykmeldte = 3,
                    sykmeldinger = listOf(
                        getArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                        getArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                    ),
                    sykmeldtFnrPrefix = "avdeling-1",
                    soknader = 1
                ) +
                    getSykmeldtData(
                        sykmeldte = 2,
                        sykmeldinger = listOf(
                            getArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                            getArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                            getArbeidsgiverSykmelding(UUID.randomUUID().toString()),
                        ),
                        sykmeldtFnrPrefix = "avdeling-2",
                        soknader = 0
                    )

            val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")

            mineSykmeldte shouldHaveSize 5
        }

        describe("sykmeldt") {
            it("should not be friskmeldt if the latest sykmeldt period is less than 16 days ago") {
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(24),
                                    tom = LocalDate.now().minusDays(16),
                                    type = PeriodetypeDTO.GRADERT,
                                    gradert = GradertDTO(50, false),
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(24),
                                    tom = LocalDate.now().minusDays(17),
                                    type = PeriodetypeDTO.GRADERT,
                                    gradert = GradertDTO(50, false),
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusDays(4),
                                    type = PeriodetypeDTO.GRADERT,
                                    gradert = GradertDTO(50, false),
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusDays(4),
                                    type = PeriodetypeDTO.GRADERT,
                                    gradert = GradertDTO(20, false),
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusDays(4),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusDays(4),
                                    type = PeriodetypeDTO.BEHANDLINGSDAGER,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusDays(4),
                                    type = PeriodetypeDTO.REISETILSKUDD,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(15),
                                    tom = LocalDate.now().minusDays(11),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(1),
                                    tom = LocalDate.now().plusDays(2),
                                    type = PeriodetypeDTO.REISETILSKUDD,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().plusDays(5),
                                    tom = LocalDate.now().plusDays(10),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(15),
                                    tom = LocalDate.now().minusDays(11),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(3),
                                    tom = LocalDate.now(),
                                    type = PeriodetypeDTO.REISETILSKUDD,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().plusDays(5),
                                    tom = LocalDate.now().plusDays(10),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(15),
                                    tom = LocalDate.now().minusDays(11),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusDays(3),
                                    type = PeriodetypeDTO.REISETILSKUDD,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().plusDays(5),
                                    tom = LocalDate.now().plusDays(10),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(20),
                                    tom = LocalDate.now().minusDays(15),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().minusDays(14),
                                    tom = LocalDate.now().minusDays(5),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().plusDays(1),
                                    tom = LocalDate.now().plusDays(4),
                                    type = PeriodetypeDTO.REISETILSKUDD,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
                every { db.getMineSykmeldte("1") } returns getSykmeldtData(
                    1,
                    listOf(
                        getArbeidsgiverSykmelding(
                            UUID.randomUUID().toString(),
                            listOf(
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().plusDays(1),
                                    tom = LocalDate.now().plusDays(4),
                                    type = PeriodetypeDTO.REISETILSKUDD,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().plusDays(5),
                                    tom = LocalDate.now().plusDays(14),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
                                ),
                                SykmeldingsperiodeAGDTO(
                                    aktivitetIkkeMulig = null,
                                    behandlingsdager = 0,
                                    fom = LocalDate.now().plusDays(15),
                                    tom = LocalDate.now().plusDays(20),
                                    type = PeriodetypeDTO.AVVENTENDE,
                                    gradert = null,
                                    innspillTilArbeidsgiver = null,
                                    reisetilskudd = false,
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
})

fun getSykmeldtData(
    sykmeldte: Int,
    sykmeldinger: List<ArbeidsgiverSykmelding> = listOf(
        getArbeidsgiverSykmelding(
            UUID.randomUUID().toString(),
        )
    ),
    soknader: Int = 0,
    sykmeldtFnrPrefix: String = "prefix"
): List<SykmeldtDbModel> =
    (0 until sykmeldte).flatMap {
        val sykmeldtFnr = "$sykmeldtFnrPrefix-$it"
        val narmestelederId = UUID.randomUUID().toString()
        val orgnummer = "orgnummer"
        val sykmeldtNavn = "Navn"
        val startDatoSykefravar = LocalDate.now()
        val orgnavn = "orgnavn"
        sykmeldinger.mapIndexed { index, arbeigsgiverSykmelding ->
            SykmeldtDbModel(
                sykmeldtFnr = sykmeldtFnr,
                narmestelederId = narmestelederId,
                orgnummer = orgnummer,
                sykmeldtNavn = sykmeldtNavn,
                startDatoSykefravar = startDatoSykefravar,
                sykmeldingId = arbeigsgiverSykmelding.id,
                orgNavn = orgnavn,
                sykmelding = arbeigsgiverSykmelding,
                soknad = if (soknader != 0 && index < soknader) getSykepengesoknadDto(
                    UUID.randomUUID().toString(),
                    arbeigsgiverSykmelding.id
                ) else null,
                lestSoknad = false,
                lestSykmelding = false,
            )
        }
    }
