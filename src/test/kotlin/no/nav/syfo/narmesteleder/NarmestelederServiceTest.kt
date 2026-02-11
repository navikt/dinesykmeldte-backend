package no.nav.syfo.narmesteleder

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class NarmestelederServiceTest :
    FunSpec({
        val database = NarmestelederDb(TestDb.database)
        val nlResponseProducer = mockk<NLResponseProducer>(relaxed = true)
        val narmestelederService = NarmestelederService(database, nlResponseProducer)

        beforeEach {
            TestDb.clearAllData()
            clearMocks(nlResponseProducer)
        }

        context("Deaktiver NL-kobling") {
            test("Legger inn ny NL-kobling") {
                val id = UUID.randomUUID()
                narmestelederService.updateNl(createNarmestelederLeesahKafkaMessage(id))

                val nlKobling = TestDb.getNarmesteleder(pasientFnr = "12345678910").first()

                nlKobling.pasientFnr shouldBeEqualTo "12345678910"
                nlKobling.lederFnr shouldBeEqualTo "01987654321"
                nlKobling.orgnummer shouldBeEqualTo "88888888"
                nlKobling.narmestelederId shouldBeEqualTo id.toString()
            }
            test("Sletter deaktivert NL-kobling") {
                val id = UUID.randomUUID()
                narmestelederService.updateNl(createNarmestelederLeesahKafkaMessage(id))
                narmestelederService.updateNl(
                    createNarmestelederLeesahKafkaMessage(id, aktivTom = LocalDate.now()),
                )

                TestDb.getNarmesteleder(pasientFnr = "12345678910").size shouldBeEqualTo 0
            }
            test(
                "Deaktiverer kobling hvis finnes aktiv NL-kobling i databasen og sletter fra databasen",
            ) {
                val id = UUID.randomUUID()
                TestDb.database.insertOrUpdate(
                    id = id.toString(),
                    orgnummer = "88888888",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )
                narmestelederService.deaktiverNarmesteLeder(
                    "01987654321",
                    id.toString(),
                    UUID.randomUUID(),
                )

                coVerify(exactly = 1) {
                    nlResponseProducer.send(
                        match {
                            it.nlAvbrutt.orgnummer == "88888888" &&
                                it.nlAvbrutt.sykmeldtFnr == "12345678910" &&
                                it.kafkaMetadata.source == "leder"
                        },
                    )
                }
                TestDb.getNarmesteleder(pasientFnr = "12345678910").size shouldBeEqualTo 0
            }
            test("Deaktiverer ikke kobling hvis NL-kobling i databasen gjelder annen ansatt") {
                val id = UUID.randomUUID()
                TestDb.database.insertOrUpdate(
                    id = id.toString(),
                    orgnummer = "88888888",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )
                narmestelederService.deaktiverNarmesteLeder(
                    "01987654321",
                    UUID.randomUUID().toString(),
                    UUID.randomUUID(),
                )

                coVerify(exactly = 0) { nlResponseProducer.send(any()) }
                TestDb.getNarmesteleder(pasientFnr = "12345678910").size shouldBeEqualTo 1
            }
        }
    })

fun createNarmestelederLeesahKafkaMessage(
    id: UUID,
    orgnummer: String = "88888888",
    fnr: String = "12345678910",
    narmesteLederFnr: String = "01987654321",
    aktivTom: LocalDate? = null,
): NarmestelederLeesahKafkaMessage =
    NarmestelederLeesahKafkaMessage(
        narmesteLederId = id,
        fnr = fnr,
        orgnummer = orgnummer,
        narmesteLederEpost = "test@nav.no",
        narmesteLederFnr = narmesteLederFnr,
        narmesteLederTelefonnummer = "12345678",
        aktivFom = LocalDate.of(2020, 1, 1),
        arbeidsgiverForskutterer = null,
        aktivTom = aktivTom,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
    )
