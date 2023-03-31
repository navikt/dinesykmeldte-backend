package no.nav.syfo.soknad

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SporsmalDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createSoknadDbModel
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.insertOrUpdateSykmeldt
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class SoknadServiceTest : FunSpec({
    val database = SoknadDb(TestDb.database)
    val soknadService = SoknadService(database)
    val sykmeldingDb = SykmeldingDb(TestDb.database)

    beforeEach {
        TestDb.clearAllData()
    }

    context("SoknadService") {
        test("Lagrer ny sendt søknad og fjerner sensitiv informasjon") {
            TestDb.database.connection.use { connection ->
                connection.insertOrUpdateSykmeldt(
                    SykmeldtDbModel(
                        "123456789",
                        "Navn",
                        LocalDate.now().minusWeeks(5),
                        LocalDate.now().minusWeeks(2),
                        null,
                    ),
                )
                connection.commit()
            }
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json"),
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusWeeks(2),
                sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
            )
            sykepengesoknadDTO.sporsmal!!.find { it.tag == ARBEID_UTENFOR_NORGE } shouldNotBeEqualTo null
            sykepengesoknadDTO.sporsmal!!.find { it.tag == ANDRE_INNTEKTSKILDER } shouldNotBeEqualTo null
            sykepengesoknadDTO.sporsmal!!.find { it.tag == ANDRE_INNTEKTSKILDER_V2 } shouldNotBeEqualTo null
            sykepengesoknadDTO.sporsmal!!.find { it.tag == UTENLANDSK_SYKMELDING_BOSTED } shouldNotBeEqualTo null
            sykepengesoknadDTO.sporsmal!!.find { it.tag == YRKESSKADE } shouldNotBeEqualTo null
            sykepengesoknadDTO.sporsmal!!.find { it.tag == UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE } shouldNotBeEqualTo null
            sykepengesoknadDTO.sporsmal!!.find { it.tag == UTENLANDSK_SYKMELDING_TRYGD_UTENFOR_NORGE } shouldNotBeEqualTo null

            soknadService.handleSykepengesoknad(sykepengesoknadDTO)

            val soknadFraDb = TestDb.getSoknad(soknadId)
            soknadFraDb?.sykmeldingId shouldBeEqualTo "76483e9f-eb16-464c-9bed-a9b258794bc4"
            soknadFraDb?.pasientFnr shouldBeEqualTo "123456789"
            soknadFraDb?.orgnummer shouldBeEqualTo "123454543"
            soknadFraDb?.sendtDato shouldBeEqualTo LocalDate.now().minusWeeks(1)
            soknadFraDb?.lest shouldBeEqualTo false
            soknadFraDb?.timestamp?.toLocalDate() shouldBeEqualTo LocalDate.now()
            soknadFraDb?.tom shouldBeEqualTo LocalDate.now().minusWeeks(2)
            val arbeidsgiverSoknadFraDb = soknadFraDb!!.soknad
            val sporsmalArbeidsgivervisning: List<SporsmalDTO> = objectMapper.readValue(
                getFileAsString("src/test/resources/soknadSporsmalArbeidsgivervisning.json"),
            )
            arbeidsgiverSoknadFraDb.andreInntektskilder shouldBeEqualTo null
            arbeidsgiverSoknadFraDb.sporsmal shouldBeEqualTo sporsmalArbeidsgivervisning
            TestDb.getSykmeldt("123456789")?.sistOppdatert shouldBeEqualTo LocalDate.now()
        }
        test("Ignorerer søknad med tom tidligere enn 4 mnd siden") {
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json"),
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(6),
                tom = LocalDate.now().minusMonths(5),
                sendtArbeidsgiver = LocalDateTime.now().minusMonths(1),
            )

            soknadService.handleSykepengesoknad(sykepengesoknadDTO)

            TestDb.getSoknad(soknadId) shouldBeEqualTo null
        }
        test("Skal lagre soknad med ny og slette med når den bare er sendt til NAV") {
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json"),
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusWeeks(2),
                sendtArbeidsgiver = null,
                status = SoknadsstatusDTO.NY,
            )
            soknadService.handleSykepengesoknad(sykepengesoknadDTO)
            TestDb.getSoknad(soknadId) shouldNotBeEqualTo null
            soknadService.handleSykepengesoknad(sykepengesoknadDTO.copy(status = SoknadsstatusDTO.SENDT))
            TestDb.getSoknad(soknadId) shouldBeEqualTo null
        }

        test("Ignorerer søknad som ikke er sendt til arbeidsgiver") {
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json"),
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusWeeks(2),
                sendtArbeidsgiver = null,
            )

            soknadService.handleSykepengesoknad(sykepengesoknadDTO)

            TestDb.getSoknad(soknadId) shouldBeEqualTo null
        }
        test("Ignorerer ikke søknad som ikke har status sendt") {
            val soknadId = UUID.randomUUID().toString()
            val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json"),
            ).copy(
                id = soknadId,
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusWeeks(2),
                sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
                status = SoknadsstatusDTO.NY,
            )

            soknadService.handleSykepengesoknad(sykepengesoknadDTO)

            TestDb.getSoknad(soknadId) shouldNotBeEqualTo null
        }

        test("ikke oppdater fnr når sykmelding ikke er i db") {
            val soknadId = UUID.randomUUID().toString()
            val soknadDbModel = createSoknadDbModel(soknadId = soknadId, pasientFnr = "OLD")
            database.insertOrUpdate(soknadDbModel)
            val soknad = TestDb.getSoknad(soknadId)
            soknad?.pasientFnr shouldBeEqualTo "OLD"
        }
        test("oppdater fnr på søknad ved mottak av søknad ved gammel fnr") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sykmelding = createSykmeldingDbModel(sykmeldingId, pasientFnr = "NEW")
            sykmeldingDb.insertOrUpdateSykmelding(sykmelding)

            val soknadId = UUID.randomUUID().toString()
            val soknadDbModel = createSoknadDbModel(soknadId = soknadId, sykmeldingId = sykmeldingId, pasientFnr = "OLD")
            database.insertOrUpdate(soknadDbModel)
            val soknad = TestDb.getSoknad(soknadId)
            soknad?.pasientFnr shouldBeEqualTo "NEW"
        }
    }
})

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
