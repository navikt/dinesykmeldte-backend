package no.nav.syfo.performancetest

import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.db.getSoknad
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.createSykmeldtDbModel
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.shouldBeEqualTo
import java.util.UUID
import kotlin.system.measureTimeMillis

@Ignored
class PerformanceTest : FunSpec({

    val database = TestDb.database
    val nlFnr = "70859400564"
    val orgnummer = "972674818"
    (0 until 1000).forEach { number ->
        val sykmeldtFnr = number.toString()
        val narmestelederId = UUID.randomUUID().toString()
        database.insertOrUpdate(
            id = narmestelederId,
            orgnummer = orgnummer,
            fnr = sykmeldtFnr,
            narmesteLederFnr = nlFnr
        )
        val sykmeldtDbModel = createSykmeldtDbModel(pasientFnr = sykmeldtFnr).copy(pasientNavn = "Navn $sykmeldtFnr")
        val sykmeldingId = UUID.randomUUID().toString()
        val sykmeldingDbModel = createSykmeldingDbModel(pasientFnr = sykmeldtFnr, orgnummer = orgnummer, sykmeldingId = sykmeldingId)
        val soknadDbModel = getSoknad(sykmeldingId = sykmeldingId).copy(
            pasientFnr = sykmeldtFnr,
            orgnummer = orgnummer
        )
        database.insertOrUpdate(sykmeldingDbModel, sykmeldtDbModel)
        database.insertOrUpdate(soknadDbModel)
    }

    val mineSykmeldteService = MineSykmeldteService(
        mineSykmeldteDb = MineSykmeldteDb(database),
    )

    context("Get mine sykmeldginer") {
        test("get mine sykmeldte") {
            val duration = measureTimeMillis {
                val sykmeldte = mineSykmeldteService.getMineSykmeldte("70859400564")
                sykmeldte.size shouldBeEqualTo 1000
            }
            System.out.println("test took $duration")
        }
    }
})
