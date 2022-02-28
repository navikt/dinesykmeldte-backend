package no.nav.syfo.virksomhet.db

import no.nav.syfo.hendelser.createSykmeldingDbModel
import no.nav.syfo.hendelser.createSykmeldtDbModel
import no.nav.syfo.narmesteleder.createNarmestelederLeesahKafkaMessage
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.util.TestDb
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

object VirksomhetDbTest : Spek({
    val virksomhetDb = VirksomhetDb(TestDb.database)
    val narmestelederDb = NarmestelederDb(TestDb.database)
    val sykmeldingDb = SykmeldingDb(TestDb.database)

    afterEachTest {
        TestDb.clearAllData()
    }

    describe("Virksomhet") {
        it("Should get virksomhet that belongs to caller") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "employee-fnr")
            narmestelederDb.insertOrUpdate(
                createNarmestelederLeesahKafkaMessage(
                    id = UUID.randomUUID(),
                    orgnummer = "right-caller-org",
                    fnr = "employee-fnr",
                    narmesteLederFnr = "test-caller-fnr"
                )
            )
            sykmeldingDb.insertOrUpdate(
                createSykmeldingDbModel(
                    sykmeldingId = UUID.randomUUID().toString(),
                    pasientFnr = "employee-fnr",
                    orgnummer = "right-caller-org",
                ),
                sykmeldt
            )

            val virksomheter = virksomhetDb.getVirksomheter("test-caller-fnr")

            virksomheter shouldHaveSize 1
            virksomheter[0].orgnummer `should be equal to` "right-caller-org"
        }

        it("Should not get virksomhet that not belongs to caller") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "employee-fnr")
            narmestelederDb.insertOrUpdate(
                createNarmestelederLeesahKafkaMessage(
                    id = UUID.randomUUID(),
                    orgnummer = "right-caller-org",
                    fnr = "employee-fnr",
                    narmesteLederFnr = "some-other-leader"
                )
            )
            sykmeldingDb.insertOrUpdate(
                createSykmeldingDbModel(
                    sykmeldingId = UUID.randomUUID().toString(),
                    pasientFnr = "employee-fnr",
                    orgnummer = "right-caller-org",
                ),
                sykmeldt
            )

            val virksomheter = virksomhetDb.getVirksomheter("test-caller-fnr")

            virksomheter shouldHaveSize 0
        }
    }
})
