package no.nav.syfo.sykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.getFileAsString
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.soknad.toSoknadDbModel
import no.nav.syfo.syketilfelle.client.SyfoSyketilfelleClient
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.pdl.model.Navn
import no.nav.syfo.sykmelding.pdl.model.PdlPerson
import no.nav.syfo.sykmelding.pdl.service.PdlPersonService
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createArbeidsgiverSykmelding
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SykmeldingServiceTest : FunSpec({
    val database = SykmeldingDb(TestDb.database)
    val pdlPersonService = mockk<PdlPersonService>()
    val syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>()
    val sykmeldingService = SykmeldingService(
        database,
        pdlPersonService,
        syfoSyketilfelleClient,
        "prod-gcp",
    )

    beforeEach {
        TestDb.clearAllData()
        clearMocks(pdlPersonService, syfoSyketilfelleClient)
        coEvery { pdlPersonService.getPerson(any(), any()) } returns PdlPerson(
            Navn("Syk", null, "Sykesen"),
            "321654987",
        )
        coEvery { syfoSyketilfelleClient.finnStartdato(any(), any()) } returns LocalDate.now().minusMonths(1)
    }

    context("SykmeldingService") {

        test("Ved oppdatering av sykmelding skal den slettes om ny TOM er eldre enn 4mnd") {
            val sykmelding = getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString())
            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmelding.kafkaMetadata.sykmeldingId, sykmelding)
            val oppdatertSykmelding = getSendtSykmeldingKafkaMessage(
                sykmelding.kafkaMetadata.sykmeldingId,
                perioder = listOf(
                    getPeriode(LocalDate.now().minusMonths(5)),
                ),
            )
            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmelding.kafkaMetadata.sykmeldingId, oppdatertSykmelding)

            val sykmeldt = TestDb.getSykmeldt("12345678910")
            sykmeldt shouldBe null
        }

        test("Skal slette sykmelding og sykmeldt ved sletting og det bare finnes en sykmelding") {
            val sykmelding = getSendtSykmeldingKafkaMessage(UUID.randomUUID().toString())
            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmelding.kafkaMetadata.sykmeldingId, sykmelding)

            val sykmeldt = TestDb.getSykmeldt("12345678910")
            sykmeldt shouldNotBe null

            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmelding.kafkaMetadata.sykmeldingId, null)

            val sykmeldt2 = TestDb.getSykmeldt("12345678910")
            sykmeldt2 shouldBe null
        }

        test("Sletting av sykmelding skal oppdatere sykmeldt latest_tom") {
            val firstFomTom = LocalDate.now().minusMonths(1)
            val firstSykmelding = getSendtSykmeldingKafkaMessage(
                UUID.randomUUID().toString(),
                perioder = listOf(
                    getPeriode(firstFomTom),
                ),
            )

            val secondTom = LocalDate.now()
            val secondSykmelding = getSendtSykmeldingKafkaMessage(
                UUID.randomUUID().toString(),
                perioder = listOf(
                    getPeriode(secondTom),
                ),
            )

            sykmeldingService.handleSendtSykmeldingKafkaMessage(firstSykmelding.sykmelding.id, firstSykmelding)
            sykmeldingService.handleSendtSykmeldingKafkaMessage(secondSykmelding.sykmelding.id, secondSykmelding)

            val sykmeldt = TestDb.getSykmeldt(firstSykmelding.kafkaMetadata.fnr)
            sykmeldt?.latestTom shouldBeEqualTo secondTom

            sykmeldingService.handleSendtSykmeldingKafkaMessage(secondSykmelding.sykmelding.id, null)

            val updatedSykmeldt = TestDb.getSykmeldt(firstSykmelding.kafkaMetadata.fnr)
            updatedSykmeldt?.latestTom shouldBeEqualTo firstFomTom
        }

        test("resending av gammel sykmelding skal ikke oppdatere latest_tom til sykmeldt") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtTilArbeidsgiverDato = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC))
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(
                sykmeldingId = sykmeldingId,
                sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
            )
            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId, sendtSykmelding)
            val fom = LocalDate.now().minusMonths(4)
            val tom = LocalDate.now().minusMonths(3)
            val oldSykmeldingg = getSendtSykmeldingKafkaMessage(
                sykmeldingId = sykmeldingId,
                sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
                perioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        fom,
                        tom,
                        null,
                        null,
                        null,
                        PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        AktivitetIkkeMuligAGDTO(null),
                        false,
                    ),
                ),
            )

            coEvery { syfoSyketilfelleClient.finnStartdato(any(), any()) } returns LocalDate.now().minusMonths(2)
            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId, oldSykmeldingg)
            val sykmeldt = TestDb.getSykmeldt("12345678910")
            sykmeldt?.pasientNavn shouldBeEqualTo "Syk Sykesen"
            sykmeldt?.latestTom shouldBeEqualTo tom
        }
        test("Lagrer ny sendt sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtTilArbeidsgiverDato = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC))
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(
                sykmeldingId = sykmeldingId,
                sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
            )

            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId, sendtSykmelding)

            val sykmeldt = TestDb.getSykmeldt("12345678910")
            sykmeldt?.pasientNavn shouldBeEqualTo "Syk Sykesen"
            sykmeldt?.startdatoSykefravaer shouldBeEqualTo LocalDate.now().minusMonths(1)
            sykmeldt?.latestTom shouldBeEqualTo LocalDate.now().plusDays(10)

            val sykmelding = TestDb.getSykmelding(sykmeldingId)
            sykmelding?.pasientFnr shouldBeEqualTo "12345678910"
            sykmelding?.orgnummer shouldBeEqualTo "88888888"
            sykmelding?.orgnavn shouldBeEqualTo "Bedriften AS"
            sykmelding?.sykmelding shouldBeEqualTo sendtSykmelding.sykmelding
            sykmelding?.lest shouldBeEqualTo false
            sykmelding?.timestamp?.toLocalDate() shouldBeEqualTo LocalDate.now()
            sykmelding?.latestTom shouldBeEqualTo LocalDate.now().plusDays(10)
            sykmelding?.sendtTilArbeidsgiverDato shouldBeEqualTo sendtTilArbeidsgiverDato
        }
        test("Oppdaterer allerede mottatt sendt sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)

            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId, sendtSykmelding)
            val sykmelding = TestDb.getSykmelding(sykmeldingId)
            sykmeldingService.handleSendtSykmeldingKafkaMessage(
                sykmeldingId,
                sendtSykmelding.copy(
                    sykmelding = sendtSykmelding.sykmelding.copy(tiltakArbeidsplassen = "Masse fine tiltak som vi glemte sist"),
                ),
            )

            val oppdatertSykmelding = TestDb.getSykmelding(sykmeldingId)
            oppdatertSykmelding?.sykmelding?.tiltakArbeidsplassen shouldBeEqualTo "Masse fine tiltak som vi glemte sist"
            oppdatertSykmelding!!.timestamp.toLocalDateTime() shouldBeAfter sykmelding!!.timestamp.toLocalDateTime()
        }
        test("Oppdaterer navn og startdato ved mottak av neste sendte sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sykmeldingId2 = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)
            val sendtSykmelding2 = getSendtSykmeldingKafkaMessage(
                sykmeldingId2,
                perioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        LocalDate.now().minusDays(10),
                        LocalDate.now().plusDays(20),
                        null,
                        null,
                        null,
                        PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        AktivitetIkkeMuligAGDTO(null),
                        false,
                    ),
                ),
            )
            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId, sendtSykmelding)

            val sykmeldt = TestDb.getSykmeldt("12345678910")
            sykmeldt?.pasientNavn shouldBeEqualTo "Syk Sykesen"
            sykmeldt?.startdatoSykefravaer shouldBeEqualTo LocalDate.now().minusMonths(1)
            sykmeldt?.latestTom shouldBeEqualTo LocalDate.now().plusDays(10)

            coEvery { pdlPersonService.getPerson(any(), any()) } returns PdlPerson(
                Navn("Per", null, "Persen"),
                "321654987",
            )
            coEvery { syfoSyketilfelleClient.finnStartdato(any(), any()) } returns LocalDate.now().minusMonths(2)

            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId2, sendtSykmelding2)

            val sykmeldtOppdatert = TestDb.getSykmeldt("12345678910")
            sykmeldtOppdatert?.pasientNavn shouldBeEqualTo "Per Persen"
            sykmeldtOppdatert?.startdatoSykefravaer shouldBeEqualTo LocalDate.now().minusMonths(2)
            sykmeldtOppdatert?.latestTom shouldBeEqualTo LocalDate.now().plusDays(20)
            sykmeldtOppdatert?.sistOppdatert shouldBeEqualTo LocalDate.now()
        }
        test("Ignorerer sendt sykmelding der tom er eldre enn fire måneder tilbake i tid") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(
                sykmeldingId,
                perioder = listOf(
                    SykmeldingsperiodeAGDTO(
                        LocalDate.now().minusMonths(8),
                        LocalDate.now().minusMonths(5),
                        null,
                        null,
                        null,
                        PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                        AktivitetIkkeMuligAGDTO(null),
                        false,
                    ),
                ),
            )
            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId, sendtSykmelding)

            TestDb.getSykmelding(sykmeldingId) shouldBeEqualTo null
        }
        test("Sletter tombstonet sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)

            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId, sendtSykmelding)
            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId, null)

            TestDb.getSykmelding(sykmeldingId) shouldBeEqualTo null
        }
        test("Oppdaterer fnr på søknad som tilhører oppdatert sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)
            sykmeldingService.handleSendtSykmeldingKafkaMessage(sykmeldingId, sendtSykmelding)
            TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))

            val sykmelding = TestDb.getSykmelding(sykmeldingId)
            sykmeldingService.handleSendtSykmeldingKafkaMessage(
                sykmeldingId,
                sendtSykmelding.copy(
                    kafkaMetadata = sendtSykmelding.kafkaMetadata.copy(fnr = "11223344556"),
                ),
            )

            val oppdatertSykmelding = TestDb.getSykmelding(sykmeldingId)
            oppdatertSykmelding?.pasientFnr shouldBeEqualTo "11223344556"
            oppdatertSykmelding!!.timestamp.toLocalDateTime() shouldBeAfter sykmelding!!.timestamp.toLocalDateTime()
            val oppdatertSoknad = TestDb.getSoknadForSykmelding(sykmeldingId)
            oppdatertSoknad?.pasientFnr shouldBeEqualTo "11223344556"
        }
    }
})

private fun getPeriode(fom: LocalDate, tom: LocalDate = fom) = SykmeldingsperiodeAGDTO(
    fom,
    tom,
    null,
    null,
    null,
    PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
    AktivitetIkkeMuligAGDTO(null),
    false,
)

fun getSendtSykmeldingKafkaMessage(
    sykmeldingId: String,
    perioder: List<SykmeldingsperiodeAGDTO> = listOf(
        SykmeldingsperiodeAGDTO(
            LocalDate.now().minusDays(2),
            LocalDate.now().plusDays(10),
            null,
            null,
            null,
            PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
            AktivitetIkkeMuligAGDTO(null),
            false,
        ),
    ),
    sendtTilArbeidsgiverDato: OffsetDateTime = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)),
) = SendtSykmeldingKafkaMessage(
    createArbeidsgiverSykmelding(sykmeldingId, perioder),
    KafkaMetadataDTO(sykmeldingId, OffsetDateTime.now(ZoneOffset.UTC), "12345678910", "user"),
    SykmeldingStatusKafkaEventDTO(
        sykmeldingId,
        sendtTilArbeidsgiverDato,
        "SENDT",
        ArbeidsgiverStatusDTO("88888888", null, "Bedriften AS"),
        null,
    ),
)

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
) = objectMapper.readValue<SykepengesoknadDTO>(
    getFileAsString("src/test/resources/soknad.json"),
).copy(
    id = soknadId,
    fnr = fnr,
    fom = LocalDate.now().minusMonths(1),
    tom = LocalDate.now().minusWeeks(2),
    sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
    sykmeldingId = sykmeldingId,
)
