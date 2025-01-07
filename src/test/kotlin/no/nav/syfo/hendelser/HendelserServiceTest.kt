package no.nav.syfo.hendelser

import io.kotest.core.spec.style.FunSpec
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.hendelser.kafka.model.DineSykmeldteHendelse
import no.nav.syfo.hendelser.kafka.model.FerdigstillHendelse
import no.nav.syfo.hendelser.kafka.model.OpprettHendelse
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.TestDb
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo

class HendelserServiceTest :
    FunSpec({
        val hendelserDb = HendelserDb(TestDb.database)
        val hendelserService = HendelserService(hendelserDb)
        val sykmeldingDb = SykmeldingDb(TestDb.database)
        afterEach { TestDb.clearAllData() }

        context("HendelseService") {
            test("Oppretter hendelse for hendelse X") {
                sykmeldingDb.insertOrUpdateSykmeldt(
                    SykmeldtDbModel(
                        "12345678910",
                        "Navn",
                        LocalDate.now().minusWeeks(5),
                        LocalDate.now().minusWeeks(2),
                        LocalDate.now(),
                    ),
                )

                val hendelseId = UUID.randomUUID().toString()
                val dineSykmeldteHendelse =
                    DineSykmeldteHendelse(
                        id = hendelseId,
                        opprettHendelse =
                            OpprettHendelse(
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
                        hendelseId = UUID.fromString(hendelseId)
                    ),
                )
                val dineSykmeldteHendelseFerdigstill =
                    DineSykmeldteHendelse(
                        id = hendelseId,
                        opprettHendelse = null,
                        ferdigstillHendelse =
                            FerdigstillHendelse(
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
                        hendelseId = UUID.fromString(hendelseId)
                    ),
                )
                val dineSykmeldteHendelseFerdigstill =
                    DineSykmeldteHendelse(
                        id = hendelseId,
                        opprettHendelse = null,
                        ferdigstillHendelse =
                            FerdigstillHendelse(
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
