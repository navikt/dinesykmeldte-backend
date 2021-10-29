package no.nav.syfo.soknad

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.mockk
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.felles.SoknadsstatusDTO
import no.nav.syfo.kafka.felles.SporsmalDTO
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.util.TestDb
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class SoknadServiceTest : Spek({
    val kafkaConsumer = mockk<KafkaConsumer<String, SykepengesoknadDTO>>(relaxed = true)
    val database = SoknadDb(TestDb.database)
    val applicationState = ApplicationState(alive = true, ready = true)
    val sykepengesoknadTopic = "topic"
    val soknadService = SoknadService(kafkaConsumer, database, applicationState, sykepengesoknadTopic)

    beforeEachTest {
        TestDb.clearAllData()
    }

    describe("SoknadService") {
        it("Lagrer ny sendt søknad og fjerner sensitiv informasjon") {
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json")
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusWeeks(2),
                sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1)
            )
            sykepengesoknadDTO.sporsmal?.find { it.tag == ARBEID_UTENFOR_NORGE } shouldNotBeEqualTo null
            sykepengesoknadDTO.sporsmal?.find { it.tag == ANDRE_INNTEKTSKILDER } shouldNotBeEqualTo null

            soknadService.handleSykepengesoknad(sykepengesoknadDTO)

            val soknadFraDb = TestDb.getSoknad(soknadId)
            soknadFraDb?.sykmeldingId shouldBeEqualTo "76483e9f-eb16-464c-9bed-a9b258794bc4"
            soknadFraDb?.pasientFnr shouldBeEqualTo "123456789"
            soknadFraDb?.orgnummer shouldBeEqualTo "123454543"
            soknadFraDb?.sendtDato shouldBeEqualTo LocalDate.now().minusWeeks(1)
            soknadFraDb?.lest shouldBeEqualTo false
            soknadFraDb?.timestamp?.toLocalDate() shouldBeEqualTo LocalDate.now()
            soknadFraDb?.latestTom shouldBeEqualTo LocalDate.now().minusWeeks(2)
            val arbeidsgiverSoknadFraDb = soknadFraDb!!.soknad
            val sporsmalArbeidsgivervisning: List<SporsmalDTO> = objectMapper.readValue(
                getFileAsString("src/test/resources/soknadSporsmalArbeidsgivervisning.json")
            )
            arbeidsgiverSoknadFraDb.andreInntektskilder shouldBeEqualTo null
            arbeidsgiverSoknadFraDb.sporsmal shouldBeEqualTo sporsmalArbeidsgivervisning
        }
        it("Ignorerer søknad med tom tidligere enn 4 mnd siden") {
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json")
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(6),
                tom = LocalDate.now().minusMonths(5),
                sendtArbeidsgiver = LocalDateTime.now().minusMonths(1)
            )

            soknadService.handleSykepengesoknad(sykepengesoknadDTO)

            TestDb.getSoknad(soknadId) shouldBeEqualTo null
        }
        it("Ignorerer søknad som ikke er sendt til arbeidsgiver") {
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json")
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusWeeks(2),
                sendtArbeidsgiver = null
            )

            soknadService.handleSykepengesoknad(sykepengesoknadDTO)

            TestDb.getSoknad(soknadId) shouldBeEqualTo null
        }
        it("Ignorerer søknad som ikke har status sendt") {
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json")
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusWeeks(2),
                sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
                status = SoknadsstatusDTO.NY
            )

            soknadService.handleSykepengesoknad(sykepengesoknadDTO)

            TestDb.getSoknad(soknadId) shouldBeEqualTo null
        }
    }
})

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
