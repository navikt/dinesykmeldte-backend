package no.nav.syfo.hendelser

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.hendelser.kafka.model.DineSykmeldteHendelse
import no.nav.syfo.hendelser.kafka.model.FerdigstillHendelse
import no.nav.syfo.hendelser.kafka.model.OpprettHendelse
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.insertOrUpdateSykmeldt
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class HendelserServiceTest : FunSpec({
    val hendelserDb = HendelserDb(TestDb.database)
    val hendelserService = HendelserService(hendelserDb)

    afterEach {
        TestDb.clearAllData()
    }

    context("HendelseService") {
        test("Oppretter hendelse for hendelse X") {
            TestDb.database.connection.use { connection ->
                connection.insertOrUpdateSykmeldt(
                    SykmeldtDbModel(
                        "12345678910",
                        "Navn",
                        LocalDate.now().minusWeeks(5),
                        LocalDate.now().minusWeeks(2),
                        null,
                    ),
                )
                connection.commit()
            }
            val hendelseId = UUID.randomUUID().toString()
            val dineSykmeldteHendelse = DineSykmeldteHendelse(
                id = hendelseId,
                opprettHendelse = OpprettHendelse(
                    ansattFnr = "12345678910",
                    orgnummer = "orgnummer",
                    oppgavetype = "HENDELSE_X",
                    lenke = null,
                    tekst = "tekst",
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    utlopstidspunkt = null,
                ),
                ferdigstillHendelse = null,
            )

            hendelserService.handleHendelse(dineSykmeldteHendelse)

            val hendelse = TestDb.getHendelse(hendelseId)
            hendelse shouldNotBeEqualTo null
            hendelse?.oppgavetype shouldBeEqualTo "HENDELSE_X"
            hendelse?.ferdigstilt shouldBeEqualTo false
            TestDb.getSykmeldt("12345678910")?.sistOppdatert shouldBeEqualTo LocalDate.now()
        }
        test("Ferdigstiller hendelse X") {
            val hendelseId = UUID.randomUUID().toString()
            val ferdigstiltTimestamp = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC))
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
                    ferdigstiltTimestamp = null,
                ),
            )
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = hendelseId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    timestamp = ferdigstiltTimestamp,
                ),
            )

            hendelserService.handleHendelse(dineSykmeldteHendelseFerdigstill)

            val hendelse = TestDb.getHendelse(hendelseId)
            hendelse shouldNotBeEqualTo null
            hendelse?.ferdigstilt shouldBeEqualTo true
            hendelse?.ferdigstiltTimestamp shouldBeEqualTo ferdigstiltTimestamp
        }
        test("Ferdigstiller ikke hendelse som allerede er ferdigstilt") {
            val hendelseId = UUID.randomUUID().toString()
            val ferdigstiltTimestamp = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC))
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
                    ferdigstiltTimestamp = ferdigstiltTimestamp,
                ),
            )
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = hendelseId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                ),
            )

            hendelserService.handleHendelse(dineSykmeldteHendelseFerdigstill)

            val hendelse = TestDb.getHendelse(hendelseId)
            hendelse shouldNotBeEqualTo null
            hendelse?.ferdigstilt shouldBeEqualTo true
            hendelse?.ferdigstiltTimestamp shouldBeEqualTo ferdigstiltTimestamp
        }
    }
})
