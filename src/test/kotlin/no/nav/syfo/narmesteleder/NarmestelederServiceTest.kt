package no.nav.syfo.narmesteleder

import io.mockk.mockk
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.util.TestDb
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class NarmestelederServiceTest : Spek({
    val database = NarmestelederDb(TestDb.database)
    val kafkaConsumer = mockk<KafkaConsumer<String, NarmestelederLeesahKafkaMessage>>(relaxed = true)
    val narmestelederService = NarmestelederService(kafkaConsumer, database, ApplicationState(alive = true, ready = true), "topic")

    beforeEachTest {
        TestDb.clearAllData()
    }

    describe("NarmestelederService") {
        it("Legger inn ny NL-kobling") {
            val id = UUID.randomUUID()
            narmestelederService.updateNl(getNarmestelederLeesahKafkaMessage(id))

            val nlKobling = TestDb.getNarmesteleder(pasientFnr = "12345678910").first()

            nlKobling.pasientFnr shouldBeEqualTo "12345678910"
            nlKobling.lederFnr shouldBeEqualTo "01987654321"
            nlKobling.orgnummer shouldBeEqualTo "88888888"
            nlKobling.narmestelederId shouldBeEqualTo id.toString()
        }
        it("Sletter deaktivert NL-kobling") {
            val id = UUID.randomUUID()
            narmestelederService.updateNl(getNarmestelederLeesahKafkaMessage(id))
            narmestelederService.updateNl(getNarmestelederLeesahKafkaMessage(id, aktivTom = LocalDate.now()))

            TestDb.getNarmesteleder(pasientFnr = "12345678910").size shouldBeEqualTo 0
        }
    }
})

fun getNarmestelederLeesahKafkaMessage(id: UUID, aktivTom: LocalDate? = null): NarmestelederLeesahKafkaMessage =
    NarmestelederLeesahKafkaMessage(
        narmesteLederId = id,
        fnr = "12345678910",
        orgnummer = "88888888",
        narmesteLederEpost = "test@nav.no",
        narmesteLederFnr = "01987654321",
        narmesteLederTelefonnummer = "12345678",
        aktivFom = LocalDate.of(2020, 1, 1),
        arbeidsgiverForskutterer = null,
        aktivTom = aktivTom,
        timestamp = OffsetDateTime.now()
    )
