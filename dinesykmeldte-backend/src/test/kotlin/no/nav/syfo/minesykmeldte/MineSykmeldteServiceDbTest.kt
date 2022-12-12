package no.nav.syfo.minesykmeldte

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.db.getSoknad
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.createSykmeldtDbModel
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class MineSykmeldteServiceDbTest : FunSpec({
    val mineSykmeldteDb = MineSykmeldteDb(TestDb.database)
    val mineSykmeldteService = MineSykmeldteService(mineSykmeldteDb)

    afterEach {
        TestDb.clearAllData()
    }

    context("MineSykmeldteService med DB") {
        test("Bruker nyeste orgnavn hvis orgnavn er forskjellig") {
            TestDb.database.insertOrUpdate(
                id = UUID.randomUUID().toString(),
                orgnummer = "orgnummer",
                fnr = "12345678910",
                narmesteLederFnr = "01987654321"
            )

            repeat(2) {
                val sykmeldingId = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(
                        sykmeldingId,
                        orgnavn = "Orgnavn 1",
                        sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1)
                    ),
                    createSykmeldtDbModel()
                )
                TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))
            }
            repeat(2) {
                val sykmeldingId = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(
                        sykmeldingId,
                        orgnavn = "Orgnavn 2",
                        sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC)
                    ),
                    createSykmeldtDbModel()
                )
                TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))
            }

            val sykmeldte = mineSykmeldteService.getMineSykmeldte("01987654321")

            sykmeldte.size shouldBeEqualTo 1
            sykmeldte[0].sykmeldinger.size shouldBeEqualTo 4
            sykmeldte[0].orgnavn shouldBeEqualTo "Orgnavn 2"
        }

        test("Bruker orgnavn med sendtTilArbeidsgiverdato hvis orgnavn er forskjellig og sendt-dato mangler") {
            TestDb.database.insertOrUpdate(
                id = UUID.randomUUID().toString(),
                orgnummer = "orgnummer",
                fnr = "12345678910",
                narmesteLederFnr = "01987654321"
            )

            repeat(2) {
                val sykmeldingId = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(
                        sykmeldingId,
                        orgnavn = "Orgnavn 1",
                        sendtTilArbeidsgiverDato = null
                    ),
                    createSykmeldtDbModel()
                )
                TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))
            }
            repeat(2) {
                val sykmeldingId = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(
                        sykmeldingId,
                        orgnavn = "Orgnavn 2",
                        sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC)
                    ),
                    createSykmeldtDbModel()
                )
                TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))
            }

            val sykmeldte = mineSykmeldteService.getMineSykmeldte("01987654321")

            sykmeldte.size shouldBeEqualTo 1
            sykmeldte[0].sykmeldinger.size shouldBeEqualTo 4
            sykmeldte[0].orgnavn shouldBeEqualTo "Orgnavn 2"
        }

        test("Tar med informasjon fra utenlandsk sykmelding") {
            TestDb.database.insertOrUpdate(
                id = UUID.randomUUID().toString(),
                orgnummer = "orgnummer",
                fnr = "12345678910",
                narmesteLederFnr = "01987654321"
            )

            val sykmeldingId = UUID.randomUUID().toString()
            TestDb.database.insertOrUpdate(
                createSykmeldingDbModel(
                    sykmeldingId,
                    orgnavn = "Orgnavn 1",
                    sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1),
                    land = "POL"
                ),
                createSykmeldtDbModel()
            )
            TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))

            val sykmeldte = mineSykmeldteService.getMineSykmeldte("01987654321")

            sykmeldte.size shouldBeEqualTo 1
            sykmeldte[0].sykmeldinger.size shouldBeEqualTo 1
            sykmeldte[0].sykmeldinger[0].behandler shouldBeEqualTo null
            sykmeldte[0].sykmeldinger[0].utenlandskSykmelding?.land shouldBeEqualTo "POL"
        }
    }
})
