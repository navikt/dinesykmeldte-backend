package no.nav.syfo.hendelser

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.hendelser.kafka.model.DineSykmeldteHendelse
import no.nav.syfo.hendelser.kafka.model.FerdigstillHendelse
import no.nav.syfo.hendelser.kafka.model.OpprettHendelse
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.soknad.getFileAsString
import no.nav.syfo.soknad.toSoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.TestDb
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
                    ferdigstiltTimestamp = null
                )
            )
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = hendelseId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    timestamp = ferdigstiltTimestamp
                )
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
                    ferdigstiltTimestamp = ferdigstiltTimestamp
                )
            )
            val dineSykmeldteHendelseFerdigstill = DineSykmeldteHendelse(
                id = hendelseId,
                opprettHendelse = null,
                ferdigstillHendelse = FerdigstillHendelse(
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
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

fun createSykmeldtDbModel(pasientFnr: String = "12345678910"): SykmeldtDbModel {
    return SykmeldtDbModel(
        pasientFnr = pasientFnr,
        pasientNavn = "Navn Navnesen",
        startdatoSykefravaer = LocalDate.now().minusMonths(2),
        latestTom = LocalDate.now().minusWeeks(2)
    )
}

fun createSoknadDbModel(
    soknadId: String,
    sykmeldingId: String = "76483e9f-eb16-464c-9bed-a9b258794bc4",
    pasientFnr: String = "123456789",
    arbeidsgivernavn: String = "Kebabbiten",
    orgnummer: String = "123454543",
): SoknadDbModel {
    val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
        getFileAsString("src/test/resources/soknad.json")
    ).copy(
        id = soknadId,
        sykmeldingId = sykmeldingId,
        fnr = pasientFnr,
        arbeidsgiver = ArbeidsgiverDTO(
            navn = arbeidsgivernavn,
            orgnummer = orgnummer,
        )
    )
    return sykepengesoknadDTO.toSoknadDbModel()
}
