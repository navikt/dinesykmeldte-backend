package no.nav.syfo.minesykmeldte.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.getNarmestelederLeesahKafkaMessage
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
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class MineSykmeldteDbTest : Spek({
    val narmestelederDb = NarmestelederDb(TestDb.database)
    val minesykmeldteDb = MineSykmeldteDb(TestDb.database)
    val sykmeldingDb = SykmeldingDb(TestDb.database)
    val soknadDb = SoknadDb(TestDb.database)

    afterEachTest {
        TestDb.clearAllData()
    }

    describe("Test getting sykmeldte from database") {
        it("Should not get any") {
            val sykmeldte = minesykmeldteDb.getMineSykmeldte("1")
            sykmeldte.size shouldBeEqualTo 0
        }
        it("Should get sykmeldte without soknad") {
            val nl = getNarmestelederLeesahKafkaMessage(UUID.randomUUID())
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
            val nl = getNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)

            val sykmeldingDbModel =
                toSykmeldingDbModel(getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString()), LocalDate.now())
            sykmeldingDb.insertOrUpdate(sykmeldingDbModel, getSykmeldt())

            soknadDb.insert(getSoknad(sykmeldingId = sykmeldingDbModel.sykmeldingId))

            val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte(nl.narmesteLederFnr)
            sykmeldtDbModel.size shouldBeEqualTo 1
            sykmeldtDbModel[0].sykmelding shouldNotBeEqualTo null
            sykmeldtDbModel[0].soknad shouldNotBeEqualTo null
        }

        it("Should get sykmeldt with 5 sykmelding and 4 soknad") {
            val nl = getNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)

            repeat(5) {
                val sykmeldingDbModel =
                    toSykmeldingDbModel(getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString()), LocalDate.now())
                sykmeldingDb.insertOrUpdate(sykmeldingDbModel, getSykmeldt())

                soknadDb.insert(getSoknad(sykmeldingId = sykmeldingDbModel.sykmeldingId))
            }
            val sykmeldingDbModel =
                toSykmeldingDbModel(getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString()), LocalDate.now())
            sykmeldingDb.insertOrUpdate(sykmeldingDbModel, getSykmeldt())

            val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte(nl.narmesteLederFnr)
            sykmeldtDbModel.size shouldBeEqualTo 6

            sykmeldtDbModel.filter { it.soknad == null }.size shouldBeEqualTo 1
        }
        it("Should get sykmelding") {
            val nl = getNarmestelederLeesahKafkaMessage(UUID.randomUUID())
            narmestelederDb.insertOrUpdate(nl)
            val sykmeldingDbModel =
                toSykmeldingDbModel(getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString()), LocalDate.now())
            sykmeldingDb.insertOrUpdate(sykmeldingDbModel, getSykmeldt())

            val sykmelding =
                minesykmeldteDb.getSykmelding(sykmeldingId = sykmeldingDbModel.sykmeldingId, nl.narmesteLederFnr)

            sykmelding shouldNotBeEqualTo null
        }
    }
})

fun getSoknad(
    sykmeldingId: String = UUID.randomUUID().toString(),
    soknadId: String = UUID.randomUUID().toString()
): SoknadDbModel {
    return getSykepengesoknadDto(soknadId, sykmeldingId).toSoknadDbModel()
}

fun getSykepengesoknadDto(
    soknadId: String,
    sykmeldingId: String
) = objectMapper.readValue<SykepengesoknadDTO>(
    getFileAsString("src/test/resources/soknad.json")
).copy(
    id = soknadId,
    fom = LocalDate.now().minusMonths(1),
    tom = LocalDate.now().minusWeeks(2),
    sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
    sykmeldingId = sykmeldingId
)

fun getSykmeldt(): SykmeldtDbModel {
    return SykmeldtDbModel(
        "12345678910",
        "Navn",
        LocalDate.now(),
        LocalDate.now()
    )
}
