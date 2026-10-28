package no.nav.syfo.narmesteleder

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.narmesteleder.client.EsyfoNarmestelederClient
import no.nav.syfo.narmesteleder.client.EsyfoNarmestelederException
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertFailsWith

class NarmestelederServiceTest :
    FunSpec({
        val database = NarmestelederDb(TestDb.database)
        val esyfoClient = mockk<EsyfoNarmestelederClient>(relaxed = true)
        val narmestelederService =
            NarmestelederService(database, esyfoClient)
        val restService = NarmestelederService(database, esyfoClient)

        beforeEach {
            TestDb.clearAllData()
            clearMocks(nlResponseProducer, esyfoClient)
        }

        context("REST revocation") {
            test("revokes remotely before deleting local rows without Kafka") {
                val id = UUID.randomUUID().toString()
                val callId = UUID.randomUUID()
                TestDb.database.insertOrUpdate(
                    id = id,
                    orgnummer = "88888888",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )
                coEvery {
                    esyfoClient.deaktiverNarmesteLeder(id, "test-user-token", callId)
                } coAnswers {
                    val relations = database.finnNarmestelederkoblinger("01987654321", id)
                    relations.size shouldBeEqualTo 1
                }

                restService.deaktiverNarmesteLeder("01987654321", id, callId, "test-user-token")

                coVerify(exactly = 1) {
                    esyfoClient.deaktiverNarmesteLeder(id, "test-user-token", callId)
                }
                coVerify(exactly = 0) { nlResponseProducer.send(any()) }
                database.finnNarmestelederkoblinger("01987654321", id).size shouldBeEqualTo 0
            }

            test("keeps local rows and propagates remote failure without Kafka") {
                val id = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    id = id,
                    orgnummer = "88888888",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )
                coEvery {
                    esyfoClient.deaktiverNarmesteLeder(any(), any(), any())
                } throws EsyfoNarmestelederException()

                assertFailsWith<EsyfoNarmestelederException> {
                    restService.deaktiverNarmesteLeder(
                        "01987654321",
                        id,
                        UUID.randomUUID(),
                        "test-user-token",
                    )
                }

                coVerify(exactly = 1) {
                    esyfoClient.deaktiverNarmesteLeder(id, "test-user-token", any())
                }
                coVerify(exactly = 0) { nlResponseProducer.send(any()) }
                database.finnNarmestelederkoblinger("01987654321", id).size shouldBeEqualTo 1
            }

            test("does not call REST when no local relation belongs to this leader") {
                val id = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(
                    id = id,
                    orgnummer = "88888888",
                    fnr = "12345678910",
                    narmesteLederFnr = "01987654321",
                )
                restService.deaktiverNarmesteLeder(
                    "01987654322",
                    id,
                    UUID.randomUUID(),
                    "test-user-token",
                )
                restService.deaktiverNarmesteLeder(
                    "01987654321",
                    UUID.randomUUID().toString(),
                    UUID.randomUUID(),
                    "test-user-token",
                )
                coVerify(exactly = 0) { nlResponseProducer.send(any()) }
                coVerify(exactly = 0) {
                    esyfoClient.deaktiverNarmesteLeder(any(), any(), any())
                }
                database.finnNarmestelederkoblinger("01987654321", id).size shouldBeEqualTo 1
            }
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
                coVerify(exactly = 0) {
                    esyfoClient.deaktiverNarmesteLeder(any(), any(), any())
                }
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
                    "test-user-token",
                )
                // TODO:
                coVerify(exactly = 1) {
                    esyfoClient.deaktiverNarmesteLeder(id.toString(), "test-user-token", any())
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
                    "test-user-token",
                )

                coVerify(exactly = 0) { nlResponseProducer.send(any()) }
                coVerify(exactly = 0) {
                    esyfoClient.deaktiverNarmesteLeder(any(), any(), any())
                }
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
