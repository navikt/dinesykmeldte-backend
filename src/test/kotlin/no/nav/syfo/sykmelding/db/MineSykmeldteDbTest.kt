package no.nav.syfo.sykmelding.db

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createArbeidsgiverSykmelding
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.createSykmeldingsperiode
import no.nav.syfo.util.createSykmeldtDbModel
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.util.UUID

class MineSykmeldteDbTest :
    FunSpec(
        {
            val sykmeldingDb = SykmeldingDb(TestDb.database)

            afterEach { TestDb.clearAllData() }

            context("Test getting sykmeldte from database") {
                test("Test get sykmeldte in last 16 days") {
                    val sykmeldingId1 = UUID.randomUUID().toString()
                    val sykmeldingId2 = UUID.randomUUID().toString()
                    val sykmeldtFnr = "12345678910"
                    val orgnummer = "123456789"

                    TestDb.database.insertOrUpdate(
                        createSykmeldingDbModel(
                            pasientFnr = sykmeldtFnr,
                            orgnummer = orgnummer,
                            sykmeldingId = sykmeldingId1,
                            latestTom = LocalDate.now().minusDays(17),
                            sykmelding =
                                createArbeidsgiverSykmelding(
                                    sykmeldingId = sykmeldingId1,
                                    perioder =
                                        listOf(
                                            createSykmeldingsperiode(
                                                fom = LocalDate.now().minusDays(18),
                                                tom = LocalDate.now().minusDays(17),
                                            ),
                                        ),
                                    land = "Norge",
                                ),
                        ),
                        createSykmeldtDbModel(sykmeldtFnr),
                    )
                    TestDb.database.insertOrUpdate(
                        createSykmeldingDbModel(
                            pasientFnr = sykmeldtFnr,
                            orgnummer = orgnummer,
                            latestTom = LocalDate.now().minusDays(16),
                            sykmeldingId = sykmeldingId2,
                            sykmelding =
                                createArbeidsgiverSykmelding(
                                    sykmeldingId = sykmeldingId1,
                                    perioder =
                                        listOf(
                                            createSykmeldingsperiode(
                                                fom = LocalDate.now().minusDays(17),
                                                tom = LocalDate.now().minusDays(16),
                                            ),
                                        ),
                                    land = "Norge",
                                ),
                        ),
                        createSykmeldtDbModel(sykmeldtFnr),
                    )

                    val allSykmeldte =
                        sykmeldingDb.getActiveSendtSykmeldingsperioder(sykmeldtFnr, orgnummer)
                    allSykmeldte?.size shouldBeEqualTo 1
                    allSykmeldte?.first() shouldBeEqualTo 1
                }

                test("Test get only sykmeldte in requested orgnummer") {
                    val sykmeldingId1 = UUID.randomUUID().toString()
                    val sykmeldingId2 = UUID.randomUUID().toString()
                    val sykmeldtFnr = "12345678910"
                    val orgnummer1 = "123456789"
                    val orgnummer2 = "223456789"

                    TestDb.database.insertOrUpdate(
                        createSykmeldingDbModel(
                            pasientFnr = sykmeldtFnr,
                            orgnummer = orgnummer1,
                            sykmeldingId = sykmeldingId1,
                        ),
                        createSykmeldtDbModel(sykmeldtFnr),
                    )
                    TestDb.database.insertOrUpdate(
                        createSykmeldingDbModel(
                            pasientFnr = sykmeldtFnr,
                            orgnummer = orgnummer2,
                            sykmeldingId = sykmeldingId2,
                        ),
                        createSykmeldtDbModel(sykmeldtFnr),
                    )

                    val allSykmeldte =
                        sykmeldingDb.getActiveSendtSykmeldingsperioder(sykmeldtFnr, orgnummer2)
                    allSykmeldte?.size shouldBeEqualTo 1
                    allSykmeldte?.first() shouldBeEqualTo 1
                }

                test("Test get only sykmeldte in requested fnr") {
                    val sykmeldingId1 = UUID.randomUUID().toString()
                    val sykmeldingId2 = UUID.randomUUID().toString()
                    val sykmeldtFnr1 = "12345678910"
                    val sykmeldtFnr2 = "22345678910"
                    val orgnummer = "123456789"

                    TestDb.database.insertOrUpdate(
                        createSykmeldingDbModel(
                            pasientFnr = sykmeldtFnr1,
                            orgnummer = orgnummer,
                            sykmeldingId = sykmeldingId1,
                        ),
                        createSykmeldtDbModel(sykmeldtFnr1),
                    )
                    TestDb.database.insertOrUpdate(
                        createSykmeldingDbModel(
                            pasientFnr = sykmeldtFnr2,
                            orgnummer = orgnummer,
                            sykmeldingId = sykmeldingId2,
                        ),
                        createSykmeldtDbModel(sykmeldtFnr2),
                    )

                    val allSykmeldte =
                        sykmeldingDb.getActiveSendtSykmeldingsperioder(sykmeldtFnr1, orgnummer)
                    allSykmeldte?.size shouldBeEqualTo 1
                    allSykmeldte?.first() shouldBeEqualTo 1
                }

                test("Should not get any") {
                    val sykmeldte = sykmeldingDb.getActiveSendtSykmeldingsperioder("1", "2")
                    sykmeldte?.size shouldBeEqualTo 1
                    sykmeldte?.first() shouldBeEqualTo 0
                }
            }
        },
    )
