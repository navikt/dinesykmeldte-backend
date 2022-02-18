package no.nav.syfo.minesykmeldte.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.common.delete.DeleteDataDb
import no.nav.syfo.hendelser.createSoknadDbModel
import no.nav.syfo.hendelser.createSykmeldingDbModel
import no.nav.syfo.hendelser.createSykmeldtDbModel
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.narmesteleder.createNarmestelederLeesahKafkaMessage
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.soknad.getFileAsString
import no.nav.syfo.soknad.toSoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.sykmelding.getSendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.mapper.SykmeldingMapper.Companion.toSykmeldingDbModel
import no.nav.syfo.util.TestDb
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class MineSykmeldteDbTest : Spek({
    val narmestelederDb = NarmestelederDb(TestDb.database)
    val minesykmeldteDb = MineSykmeldteDb(TestDb.database)
    val sykmeldingDb = SykmeldingDb(TestDb.database)
    val soknadDb = SoknadDb(TestDb.database)
    val hendelse = HendelserDb(TestDb.database)
    val deleteDataDb = DeleteDataDb(TestDb.database)

    afterEachTest {
        TestDb.clearAllData()
    }

    describe("Delete data from db") {
        it("Should not delete anything") {
            val nl = createNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)
            sykmeldingDb.insertOrUpdate(
                toSykmeldingDbModel(
                    sykmelding = getSendtSykmeldingKafkaMessage("1"), LocalDate.now().minusMonths(4)
                ),
                sykmeldt = getSykmeldt()
            )
            soknadDb.insertOrUpdate(getSoknad(sykmeldingId = "1").copy(tom = LocalDate.now().minusMonths(4)))
            hendelse.insertHendelse(
                HendelseDbModel(
                    id = "2",
                    pasientFnr = "12345678910",
                    orgnummer = "123",
                    oppgavetype = "LES_SYKMELDING",
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(),
                    utlopstidspunkt = OffsetDateTime.now().plusDays(1),
                    ferdigstilt = false,
                    ferdigstiltTimestamp = null
                )
            )

            val result = deleteDataDb.deleteOldData(LocalDate.now().minusMonths(4))

            result.deletedSykmelding shouldBeEqualTo 0
            result.deletedSykmeldt shouldBeEqualTo 0
            result.deletedHendelser shouldBeEqualTo 0
            result.deletedSoknader shouldBeEqualTo 0
        }
        it("Should delete") {
            val nl = createNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)
            val latestTom = LocalDate.now().minusMonths(4).minusDays(1)
            sykmeldingDb.insertOrUpdate(
                toSykmeldingDbModel(
                    sykmelding = getSendtSykmeldingKafkaMessage("1"), latestTom
                ),
                sykmeldt = getSykmeldt(latestTom)
            )
            soknadDb.insertOrUpdate(getSoknad(sykmeldingId = "1").copy(tom = latestTom))
            hendelse.insertHendelse(
                HendelseDbModel(
                    id = "2",
                    pasientFnr = "12345678910",
                    orgnummer = "123",
                    oppgavetype = "LES_SYKMELDING",
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(),
                    utlopstidspunkt = OffsetDateTime.now().minusDays(1),
                    ferdigstilt = false,
                    ferdigstiltTimestamp = null
                )
            )

            val result = deleteDataDb.deleteOldData(LocalDate.now().minusMonths(4))

            result.deletedSykmeldt shouldBeEqualTo 1
            result.deletedHendelser shouldBeEqualTo 1
            result.deletedSoknader shouldBeEqualTo 1
            result.deletedSykmelding shouldBeEqualTo 1
        }
        it("Skal slette hendelse hvis sykmeldt slettes") {
            val nl = createNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)
            val latestTom = LocalDate.now().minusMonths(4).minusDays(1)
            sykmeldingDb.insertOrUpdate(
                toSykmeldingDbModel(
                    sykmelding = getSendtSykmeldingKafkaMessage("1"), latestTom
                ),
                sykmeldt = getSykmeldt(latestTom)
            )
            soknadDb.insertOrUpdate(getSoknad(sykmeldingId = "1").copy(tom = latestTom))
            hendelse.insertHendelse(
                HendelseDbModel(
                    id = "2",
                    pasientFnr = "12345678910",
                    orgnummer = "123",
                    oppgavetype = "LES_SYKMELDING",
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(),
                    utlopstidspunkt = OffsetDateTime.now().plusDays(10),
                    ferdigstilt = false,
                    ferdigstiltTimestamp = null
                )
            )

            val result = deleteDataDb.deleteOldData(LocalDate.now().minusMonths(4))

            result.deletedSykmeldt shouldBeEqualTo 1
            result.deletedHendelser shouldBeEqualTo 1
            result.deletedSoknader shouldBeEqualTo 1
            result.deletedSykmelding shouldBeEqualTo 1
        }
    }

    describe("Test getting sykmeldte from database") {
        it("Should not get any") {
            val sykmeldte = minesykmeldteDb.getMineSykmeldte("1")
            sykmeldte.size shouldBeEqualTo 0
        }
        it("Should get sykmeldte without soknad") {
            val nl = createNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)
            sykmeldingDb.insertOrUpdate(
                toSykmeldingDbModel(
                    sykmelding = getSendtSykmeldingKafkaMessage("0615720a-b1a0-47e6-885c-8d927c35ef4c"), LocalDate.now()
                ),
                sykmeldt = getSykmeldt()
            )
            val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte(nl.narmesteLederFnr)
            sykmeldtDbModel.size shouldBeEqualTo 1
            sykmeldtDbModel[0].sykmelding shouldNotBeEqualTo null
            sykmeldtDbModel[0].soknad shouldBeEqualTo null
        }
        it("should get sykmeldt with soknad") {
            val nl = createNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)

            val sykmeldingDbModel =
                toSykmeldingDbModel(getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString()), LocalDate.now())
            sykmeldingDb.insertOrUpdate(sykmeldingDbModel, getSykmeldt())

            soknadDb.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingDbModel.sykmeldingId))

            val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte(nl.narmesteLederFnr)
            sykmeldtDbModel.size shouldBeEqualTo 1
            sykmeldtDbModel[0].sykmelding shouldNotBeEqualTo null
            sykmeldtDbModel[0].soknad shouldNotBeEqualTo null
        }

        it("Should get sykmeldt with 5 sykmelding and 4 soknad") {
            val nl = createNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)

            repeat(5) {
                val sykmeldingDbModel =
                    toSykmeldingDbModel(getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString()), LocalDate.now())
                sykmeldingDb.insertOrUpdate(sykmeldingDbModel, getSykmeldt())

                soknadDb.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingDbModel.sykmeldingId))
            }
            val sykmeldingDbModel =
                toSykmeldingDbModel(getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString()), LocalDate.now())
            sykmeldingDb.insertOrUpdate(sykmeldingDbModel, getSykmeldt())

            val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte(nl.narmesteLederFnr)
            sykmeldtDbModel.size shouldBeEqualTo 6

            sykmeldtDbModel.filter { it.soknad == null }.size shouldBeEqualTo 1
        }
        it("Should get sykmelding") {
            val nl = createNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)
            val sykmeldingDbModel =
                toSykmeldingDbModel(getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString()), LocalDate.now())
            sykmeldingDb.insertOrUpdate(sykmeldingDbModel, getSykmeldt())

            val sykmelding =
                minesykmeldteDb.getSykmelding(sykmeldingId = sykmeldingDbModel.sykmeldingId, nl.narmesteLederFnr)

            sykmelding shouldNotBeEqualTo null
        }
    }

    describe("Marking sykmeldinger as read") {
        it("should mark as read when sykmelding belongs to leders ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            val nl = createNarmestelederLeesahKafkaMessage(
                UUID.randomUUID(),
                fnr = "pasient-1",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            narmestelederDb.insertOrUpdate(nl)
            sykmeldingDb.insertOrUpdate(sykmelding, sykmeldt)
            val didMarkAsRead = minesykmeldteDb.markSykmeldingRead("sykmelding-id-1", "leder-fnr-1")

            didMarkAsRead.`should be true`()
        }

        it("should not mark as read when sykmelding does not belong to leders ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            val nl = createNarmestelederLeesahKafkaMessage(
                UUID.randomUUID(),
                fnr = "pasient-2",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            narmestelederDb.insertOrUpdate(nl)
            sykmeldingDb.insertOrUpdate(sykmelding, sykmeldt)
            val didMarkAsRead = minesykmeldteDb.markSykmeldingRead("sykmelding-id-1", "leder-fnr-1")

            didMarkAsRead.`should be false`()
        }
    }

    describe("Marking søknader as read") {
        it("should mark as read when søknad belongs to leders ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            val soknad = createSoknadDbModel("soknad-id-1", sykmeldingId = "sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            val nl = createNarmestelederLeesahKafkaMessage(
                UUID.randomUUID(),
                fnr = "pasient-1",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            narmestelederDb.insertOrUpdate(nl)
            sykmeldingDb.insertOrUpdate(sykmelding, sykmeldt)
            soknadDb.insertOrUpdate(soknad)
            val didMarkAsRead = minesykmeldteDb.markSoknadRead("soknad-id-1", "leder-fnr-1")

            didMarkAsRead.`should be true`()
        }

        it("should not mark as read when søknad does not belong to leders ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            val soknad = createSoknadDbModel("soknad-id-1", sykmeldingId = "sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            val nl = createNarmestelederLeesahKafkaMessage(
                UUID.randomUUID(),
                fnr = "pasient-2",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            narmestelederDb.insertOrUpdate(nl)
            sykmeldingDb.insertOrUpdate(sykmelding, sykmeldt)
            soknadDb.insertOrUpdate(soknad)
            val didMarkAsRead = minesykmeldteDb.markSoknadRead("soknad-id-1", "leder-fnr-1")

            didMarkAsRead.`should be false`()
        }
    }
})

fun getSoknad(
    sykmeldingId: String = UUID.randomUUID().toString(),
    soknadId: String = UUID.randomUUID().toString(),
): SoknadDbModel {
    return createSykepengesoknadDto(soknadId, sykmeldingId).toSoknadDbModel()
}

fun createSykepengesoknadDto(
    soknadId: String,
    sykmeldingId: String,
) = objectMapper.readValue<SykepengesoknadDTO>(
    getFileAsString("src/test/resources/soknad.json")
).copy(
    id = soknadId,
    fom = LocalDate.now().minusMonths(1),
    tom = LocalDate.now().minusWeeks(2),
    sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
    sykmeldingId = sykmeldingId
)

fun getSykmeldt(latestTom: LocalDate = LocalDate.now()): SykmeldtDbModel {
    return SykmeldtDbModel(
        "12345678910",
        "Navn",
        LocalDate.now(),
        latestTom
    )
}
