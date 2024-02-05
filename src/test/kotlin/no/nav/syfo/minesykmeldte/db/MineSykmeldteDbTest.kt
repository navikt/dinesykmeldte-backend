package no.nav.syfo.minesykmeldte.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.soknad.model.Soknad
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.testutils.getFileAsString
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createSoknadDbModel
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.createSykmeldtDbModel
import no.nav.syfo.util.insertHendelse
import no.nav.syfo.util.insertOrUpdate
import no.nav.syfo.util.toSoknadDbModel
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo

class MineSykmeldteDbTest :
    FunSpec({
        val minesykmeldteDb = MineSykmeldteDb(TestDb.database)

        afterEach { TestDb.clearAllData() }

        context("Test getting sykmeldte from database") {
            test("Should not get any") {
                val sykmeldte = minesykmeldteDb.getMineSykmeldte("1")
                sykmeldte.size shouldBeEqualTo 0
            }
            test("Should get sykmeldte without soknad") {
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    orgnummer = "orgnummer",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(
                        sykmeldingId = "0615720a-b1a0-47e6-885c-8d927c35ef4c",
                    ),
                    createSykmeldtDbModel(),
                )

                val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte("01987654321")
                sykmeldtDbModel.size shouldBeEqualTo 1
                sykmeldtDbModel[0].sykmelding shouldNotBeEqualTo null
                sykmeldtDbModel[0].soknad shouldBeEqualTo null
            }
            test("should get sykmeldt with soknad") {
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    orgnummer = "orgnummer",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )
                val sykmeldingId = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(sykmeldingId),
                    createSykmeldtDbModel()
                )

                TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))

                val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte("01987654321")
                sykmeldtDbModel.size shouldBeEqualTo 1
                sykmeldtDbModel[0].sykmelding shouldNotBeEqualTo null
                sykmeldtDbModel[0].soknad shouldNotBeEqualTo null
            }

            test("Should get sykmeldt with 5 sykmelding and 4 soknad") {
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    orgnummer = "orgnummer",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )

                repeat(5) {
                    val sykmeldingId = UUID.randomUUID().toString()
                    TestDb.database.insertOrUpdate(
                        createSykmeldingDbModel(sykmeldingId),
                        createSykmeldtDbModel()
                    )

                    TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))
                }
                val sykmeldingId = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(sykmeldingId),
                    createSykmeldtDbModel()
                )

                val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte("01987654321")
                sykmeldtDbModel.size shouldBeEqualTo 6

                sykmeldtDbModel.filter { it.soknad == null }.size shouldBeEqualTo 1
            }
            test("Should get sykmelding") {
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    orgnummer = "orgnummer",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )
                val sykmeldingId = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(sykmeldingId),
                    createSykmeldtDbModel()
                )

                val sykmelding =
                    minesykmeldteDb.getSykmelding(sykmeldingId = sykmeldingId, "01987654321")

                sykmelding shouldNotBeEqualTo null
            }
            test("henter ikke søknad på annet fnr (byttet fnr)") {
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    orgnummer = "orgnummer",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )
                val sykmeldingId = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(sykmeldingId),
                    createSykmeldtDbModel()
                )

                TestDb.database.insertOrUpdate(
                    getSoknad(sykmeldingId = sykmeldingId, fnr = "11223344556")
                )

                val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte("01987654321")
                sykmeldtDbModel.size shouldBeEqualTo 1
                sykmeldtDbModel[0].sykmelding shouldNotBeEqualTo null
                sykmeldtDbModel[0].soknad shouldBeEqualTo null
            }
        }

        context("Marking sykmeldinger as read") {
            test("should mark as read when sykmelding belongs to leders ansatt") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
                val sykmelding =
                    createSykmeldingDbModel(
                        "sykmelding-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org"
                    )
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    fnr = "pasient-1",
                    orgnummer = "kul-org",
                    narmesteLederFnr = "leder-fnr-1",
                )
                TestDb.database.insertOrUpdate(sykmelding, sykmeldt)

                val didMarkAsRead =
                    minesykmeldteDb.markSykmeldingRead("sykmelding-id-1", "leder-fnr-1")
                val oppdatertSykmelding =
                    minesykmeldteDb.getSykmelding("sykmelding-id-1", "leder-fnr-1")?.second

                didMarkAsRead.`should be true`()
                oppdatertSykmelding?.lest shouldBeEqualTo true
            }

            test("should not mark as read when sykmelding does not belong to leders ansatt") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
                val sykmelding =
                    createSykmeldingDbModel(
                        "sykmelding-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org"
                    )
                TestDb.database.insertOrUpdate(
                    UUID.randomUUID().toString(),
                    fnr = "pasient-2",
                    orgnummer = "kul-org",
                    narmesteLederFnr = "leder-fnr-1",
                )
                TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
                val didMarkAsRead =
                    minesykmeldteDb.markSykmeldingRead("sykmelding-id-1", "leder-fnr-1")

                didMarkAsRead.`should be false`()
            }
        }

        context("Marking søknader as read") {
            test("should mark as read when søknad belongs to leders ansatt") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
                val sykmelding =
                    createSykmeldingDbModel(
                        "sykmelding-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org"
                    )
                val soknad =
                    createSoknadDbModel(
                        "soknad-id-1",
                        sykmeldingId = "sykmelding-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org",
                    )
                TestDb.database.insertOrUpdate(
                    UUID.randomUUID().toString(),
                    fnr = "pasient-1",
                    orgnummer = "kul-org",
                    narmesteLederFnr = "leder-fnr-1",
                )
                TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
                TestDb.database.insertOrUpdate(soknad)
                val didMarkAsRead = minesykmeldteDb.markSoknadRead("soknad-id-1", "leder-fnr-1")
                val oppdatertSoknad =
                    minesykmeldteDb.getSoknad("soknad-id-1", "leder-fnr-1")?.second

                didMarkAsRead.`should be true`()
                oppdatertSoknad?.lest shouldBeEqualTo true
            }

            test("should not mark as read when søknad does not belong to leders ansatt") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
                val sykmelding =
                    createSykmeldingDbModel(
                        "sykmelding-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org"
                    )
                val soknad =
                    createSoknadDbModel(
                        "soknad-id-1",
                        sykmeldingId = "sykmelding-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org",
                    )
                TestDb.database.insertOrUpdate(
                    UUID.randomUUID().toString(),
                    fnr = "pasient-2",
                    orgnummer = "kul-org",
                    narmesteLederFnr = "leder-fnr-1",
                )
                TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
                TestDb.database.insertOrUpdate(soknad)
                val didMarkAsRead = minesykmeldteDb.markSoknadRead("soknad-id-1", "leder-fnr-1")

                didMarkAsRead.`should be false`()
            }
        }

        context("Markere hendelser som lest") {
            test("Skal markere hendelsen som lest hvis den tilhører lederens ansatt") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
                val sykmelding =
                    createSykmeldingDbModel(
                        "sykmelding-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org"
                    )
                TestDb.database.insertOrUpdate(
                    UUID.randomUUID().toString(),
                    fnr = "pasient-1",
                    orgnummer = "kul-org",
                    narmesteLederFnr = "leder-fnr-1",
                )
                val hendelse =
                    HendelseDbModel(
                        id = "hendelse-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org",
                        oppgavetype = "OPPGAVETYPE",
                        lenke = "https://link",
                        tekst = "tekst",
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        utlopstidspunkt = null,
                        ferdigstilt = false,
                        ferdigstiltTimestamp = null,
                        hendelseId = UUID.randomUUID(),
                    )
                TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
                TestDb.database.insertHendelse(hendelse)
                val didMarkAsRead =
                    minesykmeldteDb.markHendelseRead(hendelse.hendelseId, "leder-fnr-1")
                val hendelseErFerdigstilt = minesykmeldteDb.getHendelser("leder-fnr-1").isEmpty()

                didMarkAsRead.`should be true`()
                hendelseErFerdigstilt shouldBeEqualTo true
            }

            test("Skal ikke markere hendelsen som lest hvis den ikke tilhører lederens ansatt") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
                val sykmelding =
                    createSykmeldingDbModel(
                        "sykmelding-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org"
                    )
                TestDb.database.insertOrUpdate(
                    UUID.randomUUID().toString(),
                    fnr = "pasient-2",
                    orgnummer = "kul-org",
                    narmesteLederFnr = "leder-fnr-1",
                )
                val hendelseDbModel =
                    HendelseDbModel(
                        id = "hendelse-id-0",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org",
                        oppgavetype = "OPPGAVETYPE",
                        lenke = null,
                        tekst = null,
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        utlopstidspunkt = null,
                        ferdigstilt = false,
                        ferdigstiltTimestamp = null,
                        hendelseId = UUID.randomUUID(),
                    )
                TestDb.database.insertHendelse(
                    hendelseDbModel,
                )
                TestDb.database.insertOrUpdate(sykmelding, sykmeldt)

                val didMarkAsRead =
                    minesykmeldteDb.markHendelseRead(hendelseDbModel.hendelseId, "leder-fnr-1")

                didMarkAsRead.`should be false`()
            }
        }

        context("Hente hendelse") {
            test("Skal hente hendelse hvis utløpstidspunkt er null") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
                val sykmelding =
                    createSykmeldingDbModel(
                        "sykmelding-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org"
                    )
                TestDb.database.insertOrUpdate(
                    UUID.randomUUID().toString(),
                    fnr = "pasient-1",
                    orgnummer = "kul-org",
                    narmesteLederFnr = "leder-fnr-1",
                )
                val hendelse =
                    HendelseDbModel(
                        id = "hendelse-id-1",
                        pasientFnr = "pasient-1",
                        orgnummer = "kul-org",
                        oppgavetype = "OPPGAVETYPE",
                        lenke = "https://link",
                        tekst = "tekst",
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        utlopstidspunkt = null,
                        ferdigstilt = false,
                        ferdigstiltTimestamp = null,
                        hendelseId = UUID.randomUUID(),
                    )
                TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
                TestDb.database.insertHendelse(hendelse)

                minesykmeldteDb.getHendelser("leder-fnr-1").size shouldBeEqualTo 1
            }
        }

        context("Markerer alle sykmeldinger og søknader som lest") {
            test("marker alle sykmeldinger og soknader som lest") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "1")
                val sykmeldt2 = createSykmeldtDbModel(pasientFnr = "2")
                val sykmelding =
                    createSykmeldingDbModel(
                        UUID.randomUUID().toString(),
                        pasientFnr = "1",
                        orgnummer = "1"
                    )
                val sykmelding2 =
                    createSykmeldingDbModel(
                        UUID.randomUUID().toString(),
                        pasientFnr = "2",
                        orgnummer = "2"
                    )
                val soknad =
                    createSoknadDbModel(
                        UUID.randomUUID().toString(),
                        pasientFnr = "1",
                        sykmeldingId = sykmelding.sykmeldingId,
                        orgnummer = "1"
                    )
                val soknad2 =
                    createSoknadDbModel(
                        UUID.randomUUID().toString(),
                        pasientFnr = "2",
                        sykmeldingId = sykmelding2.sykmeldingId,
                        orgnummer = "2"
                    )

                TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
                TestDb.database.insertOrUpdate(sykmelding2, sykmeldt2)
                TestDb.database.insertOrUpdate(soknad)
                TestDb.database.insertOrUpdate(soknad2)
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    orgnummer = "1",
                    fnr = "1",
                    narmesteLederFnr = "3",
                )
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    orgnummer = "2",
                    fnr = "2",
                    narmesteLederFnr = "3",
                )
                minesykmeldteDb.markAllSykmeldingAndSoknadAsRead(lederFnr = "3")
                val mineSykmeldte = minesykmeldteDb.getMineSykmeldte("3")
                val lest = mineSykmeldte.flatMap { listOf(it.lestSoknad, it.lestSykmelding) }
                lest.all { it } shouldBeEqualTo true
            }

            test("Skal ikke markere andre sine sykmeldinger") {
                val sykmelding =
                    createSykmeldingDbModel(
                        UUID.randomUUID().toString(),
                        pasientFnr = "1",
                        orgnummer = "orgnummer"
                    )
                TestDb.database.insertOrUpdate(
                    createSoknadDbModel(
                        UUID.randomUUID().toString(),
                        sykmelding.sykmeldingId,
                        "1",
                        orgnummer = "orgnummer"
                    )
                )
                TestDb.database.insertOrUpdate(sykmelding, createSykmeldtDbModel("1"))
                TestDb.database.insertOrUpdate(
                    UUID.randomUUID().toString(),
                    "orgnummer",
                    "2",
                    "leder",
                )
                TestDb.database.insertOrUpdate(
                    UUID.randomUUID().toString(),
                    "orgnummer",
                    "1",
                    "leder-2",
                )

                minesykmeldteDb.markAllSykmeldingAndSoknadAsRead("leder")
                minesykmeldteDb
                    .getMineSykmeldte("leder-2")
                    .flatMap { listOf(it.lestSoknad, it.lestSykmelding) }
                    .all { it } shouldBeEqualTo false
                minesykmeldteDb.markAllSykmeldingAndSoknadAsRead("leder-2")
                minesykmeldteDb
                    .getMineSykmeldte("leder-2")
                    .flatMap { listOf(it.lestSoknad, it.lestSykmelding) }
                    .all { it } shouldBeEqualTo true
            }
        }
    })

fun getSoknad(
    sykmeldingId: String = UUID.randomUUID().toString(),
    soknadId: String = UUID.randomUUID().toString(),
    fnr: String = "12345678910",
): SoknadDbModel {
    return createSykepengesoknadDto(soknadId, sykmeldingId, fnr).toSoknadDbModel()
}

fun createSykepengesoknadDto(
    soknadId: String,
    sykmeldingId: String,
    fnr: String = "12345678910",
) =
    objectMapper
        .readValue<Soknad>(
            getFileAsString("src/test/resources/soknad.json"),
        )
        .copy(
            id = soknadId,
            fnr = fnr,
            fom = LocalDate.now().minusMonths(1),
            tom = LocalDate.now().minusWeeks(2),
            sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
            sykmeldingId = sykmeldingId,
        )

fun getSykmeldt(latestTom: LocalDate = LocalDate.now()): SykmeldtDbModel {
    return SykmeldtDbModel(
        "12345678910",
        "Navn",
        LocalDate.now(),
        latestTom,
    )
}
