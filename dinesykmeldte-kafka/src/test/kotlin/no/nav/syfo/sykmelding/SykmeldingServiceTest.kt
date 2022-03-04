package no.nav.syfo.sykmelding

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.syketilfelle.client.SyfoSyketilfelleClient
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.pdl.model.Navn
import no.nav.syfo.sykmelding.pdl.model.PdlPerson
import no.nav.syfo.sykmelding.pdl.service.PdlPersonService
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createArbeidsgiverSykmelding
import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SykmeldingServiceTest : Spek({
    val database = SykmeldingDb(TestDb.database)
    val pdlPersonService = mockk<PdlPersonService>()
    val syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>()
    val sykmeldingService = SykmeldingService(
        database,
        pdlPersonService,
        syfoSyketilfelleClient,
        "prod-gcp"
    )

    beforeEachTest {
        TestDb.clearAllData()
        clearMocks(pdlPersonService, syfoSyketilfelleClient)
        coEvery { pdlPersonService.getPerson(any(), any()) } returns PdlPerson(
            Navn("Syk", null, "Sykesen"),
            "321654987"
        )
        coEvery { syfoSyketilfelleClient.finnStartdato(any(), any()) } returns LocalDate.now().minusMonths(1)
    }

    describe("SykmeldingService") {
        it("Lagrer ny sendt sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)
            runBlocking {
                sykmeldingService.handleSendtSykmelding(sykmeldingId, sendtSykmelding)

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
            }
        }
        it("Oppdaterer allerede mottatt sendt sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)
            runBlocking {
                sykmeldingService.handleSendtSykmelding(sykmeldingId, sendtSykmelding)
                val sykmelding = TestDb.getSykmelding(sykmeldingId)
                sykmeldingService.handleSendtSykmelding(
                    sykmeldingId,
                    sendtSykmelding.copy(
                        sykmelding = sendtSykmelding.sykmelding.copy(tiltakArbeidsplassen = "Masse fine tiltak som vi glemte sist")
                    )
                )

                val oppdatertSykmelding = TestDb.getSykmelding(sykmeldingId)
                oppdatertSykmelding?.sykmelding?.tiltakArbeidsplassen shouldBeEqualTo "Masse fine tiltak som vi glemte sist"
                oppdatertSykmelding!!.timestamp.toLocalDateTime() shouldBeAfter sykmelding!!.timestamp.toLocalDateTime()
            }
        }
        it("Oppdaterer navn og startdato ved mottak av neste sendte sykmelding") {
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
                        false
                    )
                )
            )
            runBlocking {
                sykmeldingService.handleSendtSykmelding(sykmeldingId, sendtSykmelding)

                val sykmeldt = TestDb.getSykmeldt("12345678910")
                sykmeldt?.pasientNavn shouldBeEqualTo "Syk Sykesen"
                sykmeldt?.startdatoSykefravaer shouldBeEqualTo LocalDate.now().minusMonths(1)
                sykmeldt?.latestTom shouldBeEqualTo LocalDate.now().plusDays(10)

                coEvery { pdlPersonService.getPerson(any(), any()) } returns PdlPerson(
                    Navn("Per", null, "Persen"),
                    "321654987"
                )
                coEvery { syfoSyketilfelleClient.finnStartdato(any(), any()) } returns LocalDate.now().minusMonths(2)

                sykmeldingService.handleSendtSykmelding(sykmeldingId2, sendtSykmelding2)

                val sykmeldtOppdatert = TestDb.getSykmeldt("12345678910")
                sykmeldtOppdatert?.pasientNavn shouldBeEqualTo "Per Persen"
                sykmeldtOppdatert?.startdatoSykefravaer shouldBeEqualTo LocalDate.now().minusMonths(2)
                sykmeldtOppdatert?.latestTom shouldBeEqualTo LocalDate.now().plusDays(20)
            }
        }
        it("Ignorerer sendt sykmelding der tom er eldre enn fire måneder tilbake i tid") {
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
                        false
                    )
                )
            )
            runBlocking {
                sykmeldingService.handleSendtSykmelding(sykmeldingId, sendtSykmelding)

                TestDb.getSykmelding(sykmeldingId) shouldBeEqualTo null
            }
        }
        it("Sletter tombstonet sykmelding") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)
            runBlocking {
                sykmeldingService.handleSendtSykmelding(sykmeldingId, sendtSykmelding)
                sykmeldingService.handleSendtSykmelding(sykmeldingId, null)

                TestDb.getSykmelding(sykmeldingId) shouldBeEqualTo null
            }
        }
    }
})

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
            false
        )
    )
) = SendtSykmeldingKafkaMessage(
    createArbeidsgiverSykmelding(sykmeldingId, perioder),
    KafkaMetadataDTO(sykmeldingId, OffsetDateTime.now(ZoneOffset.UTC), "12345678910", "user"),
    SykmeldingStatusKafkaEventDTO(
        sykmeldingId,
        OffsetDateTime.now(ZoneOffset.UTC),
        "SENDT",
        ArbeidsgiverStatusDTO("88888888", null, "Bedriften AS"),
        null
    )
)
