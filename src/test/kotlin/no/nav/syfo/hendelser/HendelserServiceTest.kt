package no.nav.syfo.hendelser

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.mockk
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.hendelser.kafka.model.DineSykmeldteHendelse
import no.nav.syfo.hendelser.kafka.model.FerdigstillHendelse
import no.nav.syfo.hendelser.kafka.model.OpprettHendelse
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.soknad.getFileAsString
import no.nav.syfo.soknad.toSoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.sykmelding.getArbeidsgiverSykmelding
import no.nav.syfo.util.TestDb
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class HendelserServiceTest : Spek({
    val sykmeldingDb = SykmeldingDb(TestDb.database)
    val soknadDb = SoknadDb(TestDb.database)
    val hendelserDb = HendelserDb(TestDb.database)
    val kafkaConsumer = mockk<KafkaConsumer<String, DineSykmeldteHendelse>>(relaxed = true)
    val hendelserService = HendelserService(kafkaConsumer, hendelserDb, ApplicationState(alive = true, ready = true), "hendelser-topic")

    afterEachTest {
        TestDb.clearAllData()
    }

    describe("HendelseService") {
        it("Oppretter hendelse for hendelse X") {
            val hendelseId = UUID.randomUUID().toString()
            val dineSykmeldteHendelse = DineSykmeldteHendelse(
                id = hendelseId,
                opprettHendelse = OpprettHendelse(
                    id = hendelseId,
                    ansattFnr = "12345678910",
                    orgnummer = "orgnummer",
                    oppgavetype = "HENDELSE_X",
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    utlopstidspunkt = null
                ),
                ferdigstillHendelse = null
            )

            hendelserService.handleHendelse(dineSykmeldteHendelse)

            val hendelse = TestDb.getHendelse(hendelseId)
            hendelse shouldNotBeEqualTo null
            hendelse?.oppgavetype shouldBeEqualTo "HENDELSE_X"
            hendelse?.ferdigstilt shouldBeEqualTo false
        }
        it("Oppretter ikke hendelse for les av sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val dineSykmeldteHendelse = DineSykmeldteHendelse(
                id = sykmeldingId,
                opprettHendelse = OpprettHendelse(
                    id = sykmeldingId,
                    ansattFnr = null,
                    orgnummer = null,
                    oppgavetype = OPPGAVETYPE_LES_SYKMELDING,
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    utlopstidspunkt = null
                ),
                ferdigstillHendelse = null
            )

            hendelserService.handleHendelse(dineSykmeldteHendelse)

            val hendelse = TestDb.getHendelse(sykmeldingId)
            hendelse shouldBeEqualTo null
        }
        it("Oppretter ikke hendelse for les av søknad") {
            val soknadId = UUID.randomUUID().toString()
            val dineSykmeldteHendelse = DineSykmeldteHendelse(
                id = soknadId,
                opprettHendelse = OpprettHendelse(
                    id = soknadId,
                    ansattFnr = null,
                    orgnummer = null,
                    oppgavetype = OPPGAVETYPE_LES_SOKNAD,
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    utlopstidspunkt = null
                ),
                ferdigstillHendelse = null
            )

            hendelserService.handleHendelse(dineSykmeldteHendelse)

            val hendelse = TestDb.getHendelse(soknadId)
            hendelse shouldBeEqualTo null
        }
        it("Ferdigstilling av les sykmelding-hendelse setter sykmelding som lest") {
            val sykmeldingId = UUID.randomUUID().toString()
            sykmeldingDb.insertOrUpdate(getSykmeldingDbModel(sykmeldingId), getSykmeldtDbModel())
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = sykmeldingId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    id = sykmeldingId,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    oppgavetype = OPPGAVETYPE_LES_SYKMELDING
                )
            )

            hendelserService.handleHendelse(dineSykmeldteHendelseFerdigstill)

            val hendelse = TestDb.getHendelse(sykmeldingId)
            hendelse shouldBeEqualTo null
            val sykmelding = TestDb.getSykmelding(sykmeldingId)
            sykmelding?.lest shouldBeEqualTo true
        }
        it("Ferdigstilling av les sykmelding-hendelse setter sykmelding som lest hvis oppgavetype mangler") {
            val sykmeldingId = UUID.randomUUID().toString()
            sykmeldingDb.insertOrUpdate(getSykmeldingDbModel(sykmeldingId), getSykmeldtDbModel())
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = sykmeldingId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    id = sykmeldingId,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    oppgavetype = null
                )
            )

            hendelserService.handleHendelse(dineSykmeldteHendelseFerdigstill)

            val hendelse = TestDb.getHendelse(sykmeldingId)
            hendelse shouldBeEqualTo null
            val sykmelding = TestDb.getSykmelding(sykmeldingId)
            sykmelding?.lest shouldBeEqualTo true
        }
        it("Ferdigstilling av les søknad-hendelse setter søknad som lest") {
            val soknadId = UUID.randomUUID().toString()
            soknadDb.insert(getSoknadDbModel(soknadId))
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = soknadId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    id = soknadId,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    oppgavetype = OPPGAVETYPE_LES_SOKNAD
                )
            )

            hendelserService.handleHendelse(dineSykmeldteHendelseFerdigstill)

            val hendelse = TestDb.getHendelse(soknadId)
            hendelse shouldBeEqualTo null
            val soknad = TestDb.getSoknad(soknadId)
            soknad?.lest shouldBeEqualTo true
        }
        it("Ferdigstilling av les søknad-hendelse setter søknad som lest hvis oppgavetype mangler") {
            val soknadId = UUID.randomUUID().toString()
            soknadDb.insert(getSoknadDbModel(soknadId))
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = soknadId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    id = soknadId,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    oppgavetype = null
                )
            )

            hendelserService.handleHendelse(dineSykmeldteHendelseFerdigstill)

            val hendelse = TestDb.getHendelse(soknadId)
            hendelse shouldBeEqualTo null
            val soknad = TestDb.getSoknad(soknadId)
            soknad?.lest shouldBeEqualTo true
        }
        it("Ferdigstiller hendelse X") {
            val hendelseId = UUID.randomUUID().toString()
            val ferdigstiltTimestamp = OffsetDateTime.now(ZoneOffset.UTC)
            hendelserDb.insertHendelse(
                HendelseDbModel(
                    id = hendelseId,
                    pasientFnr = "12345678910",
                    orgnummer = "orgnummer",
                    oppgavetype = "HENDELSE_X",
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3),
                    utlopstidspunkt = null,
                    ferdigstilt = false,
                    ferdigstiltTimestamp = null
                )
            )
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = hendelseId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    id = hendelseId,
                    timestamp = ferdigstiltTimestamp,
                    oppgavetype = "HENDELSE_X"
                )
            )

            hendelserService.handleHendelse(dineSykmeldteHendelseFerdigstill)

            val hendelse = TestDb.getHendelse(hendelseId)
            hendelse shouldNotBeEqualTo null
            hendelse?.ferdigstilt shouldBeEqualTo true
            hendelse?.ferdigstiltTimestamp shouldBeEqualTo ferdigstiltTimestamp
        }
        it("Ferdigstiller ikke hendelse som allerede er ferdigstilt") {
            val hendelseId = UUID.randomUUID().toString()
            val ferdigstiltTimestamp = OffsetDateTime.now(ZoneOffset.UTC)
            hendelserDb.insertHendelse(
                HendelseDbModel(
                    id = hendelseId,
                    pasientFnr = "12345678910",
                    orgnummer = "orgnummer",
                    oppgavetype = "HENDELSE_X",
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3),
                    utlopstidspunkt = null,
                    ferdigstilt = true,
                    ferdigstiltTimestamp = ferdigstiltTimestamp
                )
            )
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = hendelseId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    id = hendelseId,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                    oppgavetype = "HENDELSE_X"
                )
            )

            hendelserService.handleHendelse(dineSykmeldteHendelseFerdigstill)

            val hendelse = TestDb.getHendelse(hendelseId)
            hendelse shouldNotBeEqualTo null
            hendelse?.ferdigstilt shouldBeEqualTo true
            hendelse?.ferdigstiltTimestamp shouldBeEqualTo ferdigstiltTimestamp
        }
    }
})

fun getSykmeldingDbModel(sykmeldingId: String): SykmeldingDbModel {
    return SykmeldingDbModel(
        sykmeldingId = sykmeldingId,
        pasientFnr = "12345678910",
        orgnummer = "orgnummer",
        orgnavn = "Navn AS",
        sykmelding = getArbeidsgiverSykmelding(sykmeldingId = sykmeldingId),
        lest = false,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
        latestTom = LocalDate.now().minusWeeks(2)
    )
}

fun getSykmeldtDbModel(): SykmeldtDbModel {
    return SykmeldtDbModel(
        pasientFnr = "12345678910",
        pasientNavn = "Navn Navnesen",
        startdatoSykefravaer = LocalDate.now().minusMonths(2),
        latestTom = LocalDate.now().minusWeeks(2)
    )
}

fun getSoknadDbModel(soknadId: String): SoknadDbModel {
    val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
        getFileAsString("src/test/resources/soknad.json")
    ).copy(
        id = soknadId
    )
    return sykepengesoknadDTO.toSoknadDbModel()
}
