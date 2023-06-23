package no.nav.syfo.virksomhet.db

import io.kotest.core.spec.style.FunSpec
import java.util.UUID
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.createSykmeldtDbModel
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize

object VirksomhetDbTest :
    FunSpec({
        val virksomhetDb = VirksomhetDb(TestDb.database)

        afterEach { TestDb.clearAllData() }

        context("Virksomhet") {
            test("Should get virksomhet that belongs to caller") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "employee-fnr")
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    orgnummer = "right-caller-org",
                    fnr = "employee-fnr",
                    narmesteLederFnr = "test-caller-fnr",
                )
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(
                        sykmeldingId = UUID.randomUUID().toString(),
                        pasientFnr = "employee-fnr",
                        orgnummer = "right-caller-org",
                    ),
                    sykmeldt,
                )

                val virksomheter = virksomhetDb.getVirksomheter("test-caller-fnr")

                virksomheter shouldHaveSize 1
                virksomheter[0].orgnummer `should be equal to` "right-caller-org"
            }

            test("Should not get virksomhet that not belongs to caller") {
                val sykmeldt = createSykmeldtDbModel(pasientFnr = "employee-fnr")
                TestDb.database.insertOrUpdate(
                    id = UUID.randomUUID().toString(),
                    orgnummer = "right-caller-org",
                    fnr = "employee-fnr",
                    narmesteLederFnr = "some-other-leader",
                )
                TestDb.database.insertOrUpdate(
                    createSykmeldingDbModel(
                        sykmeldingId = UUID.randomUUID().toString(),
                        pasientFnr = "employee-fnr",
                        orgnummer = "right-caller-org",
                    ),
                    sykmeldt,
                )

                val virksomheter = virksomhetDb.getVirksomheter("test-caller-fnr")

                virksomheter shouldHaveSize 0
            }
        }
    })
