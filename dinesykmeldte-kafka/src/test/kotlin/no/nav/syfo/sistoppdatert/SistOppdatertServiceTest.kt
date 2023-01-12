package no.nav.syfo.sistoppdatert

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.getFileAsString
import no.nav.syfo.soknad.toSoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.sykmelding.getSendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.mapper.SykmeldingMapper
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.insert
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SistOppdatertServiceTest : FunSpec({
    val database = SistOppdatertDb(TestDb.database)
    val sistOppdatertService = SistOppdatertService(database)

    beforeEach {
        TestDb.clearAllData()
    }

    context("SistOppdatertService") {
        test("Skal ikke endre noe hvis både sykmeldt og sykmelding mangler") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)

            sistOppdatertService.oppdaterSykmeldtOgSykmelding(sykmeldingId, sykmelding)

            TestDb.getSykmeldt("12345678910") shouldBeEqualTo null
            TestDb.getSykmelding(sykmeldingId) shouldBeEqualTo null
        }
        test("Oppdaterer sykmeldt og sykmelding hvis bruker kun har en sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId, sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2))
            val sykmeldingDbModel = SykmeldingMapper.toSykmeldingDbModel(sykmelding, LocalDate.now().plusDays(10))
                .copy(sendtTilArbeidsgiverDato = null, timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
            TestDb.database.insertOrUpdate(
                sykmeldingDbModel,
                SykmeldtDbModel(
                    pasientFnr = "12345678910",
                    pasientNavn = "Navn",
                    startdatoSykefravaer = LocalDate.now().minusDays(2),
                    latestTom = LocalDate.now().plusDays(10),
                    sistOppdatert = null
                )
            )

            sistOppdatertService.oppdaterSykmeldtOgSykmelding(sykmeldingId, sykmelding)

            TestDb.getSykmeldt("12345678910")?.sistOppdatert shouldBeEqualTo LocalDate.now().minusDays(1)
            TestDb.getSykmelding(sykmeldingId)?.sendtTilArbeidsgiverDato?.toLocalDate() shouldBeEqualTo LocalDate.now().minusDays(2)
        }
        test("Oppdaterer sykmeldt og sykmelding hvis bruker har en sykmelding, en søknad og en hendelse") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId, sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2))
            val sykmeldingDbModel = SykmeldingMapper.toSykmeldingDbModel(sykmelding, LocalDate.now().plusDays(10))
                .copy(sendtTilArbeidsgiverDato = null, timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2))
            TestDb.database.insertOrUpdate(
                sykmeldingDbModel,
                SykmeldtDbModel(
                    pasientFnr = "12345678910",
                    pasientNavn = "Navn",
                    startdatoSykefravaer = LocalDate.now().minusDays(2),
                    latestTom = LocalDate.now().plusDays(10),
                    sistOppdatert = null
                )
            )
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json")
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusWeeks(2),
                sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
                fnr = "12345678910"
            )
            TestDb.database.insertOrUpdate(sykepengesoknadDTO.toSoknadDbModel().copy(timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)))
            TestDb.database.insert(
                HendelseDbModel(
                    id = UUID.randomUUID().toString(),
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

            sistOppdatertService.oppdaterSykmeldtOgSykmelding(sykmeldingId, sykmelding)

            TestDb.getSykmeldt("12345678910")?.sistOppdatert shouldBeEqualTo LocalDate.now().minusDays(1)
            TestDb.getSykmelding(sykmeldingId)?.sendtTilArbeidsgiverDato?.toLocalDate() shouldBeEqualTo LocalDate.now().minusDays(2)
        }
        test("Oppdaterer sykmeldt og sykmelding hvis bruker har tre sykmeldinger og en hendelse") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId, sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(4))
            val sykmeldingDbModel = SykmeldingMapper.toSykmeldingDbModel(sykmelding, LocalDate.now().minusWeeks(3))
                .copy(sendtTilArbeidsgiverDato = null, timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(4))
            TestDb.database.insertOrUpdate(
                sykmeldingDbModel,
                SykmeldtDbModel(
                    pasientFnr = "12345678910",
                    pasientNavn = "Navn",
                    startdatoSykefravaer = LocalDate.now().minusWeeks(4),
                    latestTom = LocalDate.now().minusWeeks(3),
                    sistOppdatert = null
                )
            )
            val sykmeldingId2 = UUID.randomUUID().toString()
            val sykmelding2 = getSendtSykmeldingKafkaMessage(sykmeldingId2, sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(2))
            val sykmeldingDbModel2 = SykmeldingMapper.toSykmeldingDbModel(sykmelding2, LocalDate.now().minusWeeks(1))
                .copy(sendtTilArbeidsgiverDato = null, timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(2))
            TestDb.database.insertOrUpdate(
                sykmeldingDbModel2,
                SykmeldtDbModel(
                    pasientFnr = "12345678910",
                    pasientNavn = "Navn",
                    startdatoSykefravaer = LocalDate.now().minusWeeks(4),
                    latestTom = LocalDate.now().minusWeeks(1),
                    sistOppdatert = null
                )
            )
            val sykmeldingId3 = UUID.randomUUID().toString()
            val sykmelding3 = getSendtSykmeldingKafkaMessage(sykmeldingId3, sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2))
            val sykmeldingDbModel3 = SykmeldingMapper.toSykmeldingDbModel(sykmelding3, LocalDate.now().plusDays(10))
                .copy(sendtTilArbeidsgiverDato = null, timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2))
            TestDb.database.insertOrUpdate(
                sykmeldingDbModel3,
                SykmeldtDbModel(
                    pasientFnr = "12345678910",
                    pasientNavn = "Navn",
                    startdatoSykefravaer = LocalDate.now().minusWeeks(4),
                    latestTom = LocalDate.now().plusDays(10),
                    sistOppdatert = null
                )
            )
            TestDb.database.insert(
                HendelseDbModel(
                    id = UUID.randomUUID().toString(),
                    pasientFnr = "12345678910",
                    orgnummer = "orgnummer",
                    oppgavetype = "HENDELSE_X",
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                    utlopstidspunkt = null,
                    ferdigstilt = false,
                    ferdigstiltTimestamp = null
                )
            )

            sistOppdatertService.oppdaterSykmeldtOgSykmelding(sykmeldingId, sykmelding)

            TestDb.getSykmeldt("12345678910")?.sistOppdatert shouldBeEqualTo LocalDate.now().minusDays(1)
            TestDb.getSykmelding(sykmeldingId)?.sendtTilArbeidsgiverDato?.toLocalDate() shouldBeEqualTo LocalDate.now().minusWeeks(4)
            TestDb.getSykmelding(sykmeldingId2)?.sendtTilArbeidsgiverDato shouldBeEqualTo null
            TestDb.getSykmelding(sykmeldingId3)?.sendtTilArbeidsgiverDato shouldBeEqualTo null
        }
        test("Oppdaterer sykmeldt, men ikke sykmelding hvis sendt til arbeidsgiver er satt") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId, sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
            val sykmeldingDbModel = SykmeldingMapper.toSykmeldingDbModel(sykmelding, LocalDate.now().plusDays(10))
                .copy(sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2), timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
            TestDb.database.insertOrUpdate(
                sykmeldingDbModel,
                SykmeldtDbModel(
                    pasientFnr = "12345678910",
                    pasientNavn = "Navn",
                    startdatoSykefravaer = LocalDate.now().minusDays(2),
                    latestTom = LocalDate.now().plusDays(10),
                    sistOppdatert = null
                )
            )

            sistOppdatertService.oppdaterSykmeldtOgSykmelding(sykmeldingId, sykmelding)

            TestDb.getSykmeldt("12345678910")?.sistOppdatert shouldBeEqualTo LocalDate.now().minusDays(1)
            TestDb.getSykmelding(sykmeldingId)?.sendtTilArbeidsgiverDato?.toLocalDate() shouldBeEqualTo LocalDate.now().minusDays(2)
        }
        test("Oppdaterer sykmelding, men ikke sykmeldt hvis sist oppdatert er satt og sendt til arbeidsgiver mangler") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId, sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
            val sykmeldingDbModel = SykmeldingMapper.toSykmeldingDbModel(sykmelding, LocalDate.now().plusDays(10))
                .copy(sendtTilArbeidsgiverDato = null, timestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
            TestDb.database.insertOrUpdate(
                sykmeldingDbModel,
                SykmeldtDbModel(
                    pasientFnr = "12345678910",
                    pasientNavn = "Navn",
                    startdatoSykefravaer = LocalDate.now().minusDays(2),
                    latestTom = LocalDate.now().plusDays(10),
                    sistOppdatert = LocalDate.now()
                )
            )

            sistOppdatertService.oppdaterSykmeldtOgSykmelding(sykmeldingId, sykmelding)

            TestDb.getSykmeldt("12345678910")?.sistOppdatert shouldBeEqualTo LocalDate.now()
            TestDb.getSykmelding(sykmeldingId)?.sendtTilArbeidsgiverDato?.toLocalDate() shouldBeEqualTo LocalDate.now().minusDays(1)
        }
    }
})
