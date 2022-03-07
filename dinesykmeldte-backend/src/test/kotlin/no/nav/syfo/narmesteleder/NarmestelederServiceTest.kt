package no.nav.syfo.narmesteleder

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.NLResponseProducer
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class NarmestelederServiceTest : Spek({
    val database = NarmestelederDb(TestDb.database)
    val nlResponseProducer = mockk<NLResponseProducer>(relaxed = true)
    val narmestelederService = NarmestelederService(database, nlResponseProducer)

    beforeEachTest {
        TestDb.clearAllData()
        clearMocks(nlResponseProducer)
    }

    describe("Deaktiver NL-kobling") {
        it("Deaktiverer kobling hvis finnes aktiv NL-kobling i databasen og sletter fra databasen") {
            val id = UUID.randomUUID()
            database.insertOrUpdate(
                id = id.toString(),
                orgnummer = "88888888",
                fnr = "12345678910",
                narmesteLederFnr = "01987654321"
            )
            narmestelederService.deaktiverNarmesteLeder("01987654321", id.toString(), UUID.randomUUID())

            verify(exactly = 1) {
                nlResponseProducer.send(
                    match {
                        it.nlAvbrutt.orgnummer == "88888888" && it.nlAvbrutt.sykmeldtFnr == "12345678910" && it.kafkaMetadata.source == "leder"
                    }
                )
            }
            TestDb.getNarmesteleder(pasientFnr = "12345678910").size shouldBeEqualTo 0
        }
        it("Deaktiverer ikke kobling hvis NL-kobling i databasen gjelder annen ansatt") {
            val id = UUID.randomUUID()
            database.insertOrUpdate(
                id = id.toString(),
                orgnummer = "88888888",
                fnr = "12345678910",
                narmesteLederFnr = "01987654321"
            )
            narmestelederService.deaktiverNarmesteLeder("01987654321", UUID.randomUUID().toString(), UUID.randomUUID())

            verify(exactly = 0) { nlResponseProducer.send(any()) }
            TestDb.getNarmesteleder(pasientFnr = "12345678910").size shouldBeEqualTo 1
        }
    }
})
