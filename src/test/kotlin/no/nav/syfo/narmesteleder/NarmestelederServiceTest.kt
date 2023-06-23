package no.nav.syfo.narmesteleder

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.NLResponseProducer
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.shouldBeEqualTo

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
            test(
                "Deaktiverer kobling hvis finnes aktiv NL-kobling i databasen og sletter fra databasen"
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
                    UUID.randomUUID()
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
                    UUID.randomUUID()
                )

                coVerify(exactly = 0) { nlResponseProducer.send(any()) }
                TestDb.getNarmesteleder(pasientFnr = "12345678910").size shouldBeEqualTo 1
            }
        }
    })
