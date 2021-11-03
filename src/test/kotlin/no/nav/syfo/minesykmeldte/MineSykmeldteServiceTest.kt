package no.nav.syfo.minesykmeldte

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.db.SykmeldtDbModel
import no.nav.syfo.minesykmeldte.db.getSykepengesoknadDto
import no.nav.syfo.sykmelding.getArbeidsgiverSykmelding
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.UUID

class MineSykmeldteServiceTest : Spek({
    val db = mockk<MineSykmeldteDb>()
    val minesykmeldtService = MineSykmeldteService(db)

    afterEachTest {
        clearMocks(db)
    }

    describe("Test minesykmeldteservice") {
        it("Should get empty list") {
            every { db.getMineSykmeldte("1") } returns emptyList()
            minesykmeldtService.getMineSykmeldte("1").size shouldBeEqualTo 0
        }
        it("should get one sykmeldt") {
            every { db.getMineSykmeldte("1") } returns getSykmeldtData(1)
            val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
            mineSykmeldte.size shouldBeEqualTo 1
            mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "100%"
        }
        it("should get one sykmeldt with 50% type") {
            every { db.getMineSykmeldte("1") } returns getSykmeldtData(1)
            val mineSykmeldte = minesykmeldtService.getMineSykmeldte("1")
            mineSykmeldte.size shouldBeEqualTo 1
            mineSykmeldte.first().previewSykmeldinger.first().type shouldBeEqualTo "50%"
        }
    }
})

fun getSykmeldtData(sykmeldte: Int, sykmeldinger: Int = 1, soknader: Int = 0): List<SykmeldtDbModel> =
    (0..sykmeldte).flatMap {
        val sykmeldtFnr = "$it"
        val narmestelederId = UUID.randomUUID().toString()
        val orgnummer = "orgnummer"
        val sykmeldtNavn = "Navn"
        val startDatoSykefravar = LocalDate.now()
        val sykmeldingId = UUID.randomUUID().toString()
        val orgnavn = "orgnavn"
        (0..sykmeldinger).map { index ->
            val sykmelding = getArbeidsgiverSykmelding(UUID.randomUUID().toString())
            SykmeldtDbModel(
                sykmeldtFnr = sykmeldtFnr,
                narmestelederId = narmestelederId,
                orgnummer = orgnummer,
                sykmeldtNavn = sykmeldtNavn,
                startDatoSykefravar = startDatoSykefravar,
                sykmeldingId = sykmeldingId,
                orgNavn = orgnavn,
                sykmelding = sykmelding,
                soknad = if (soknader != 0 && soknader <= index) getSykepengesoknadDto(
                    UUID.randomUUID().toString(),
                    sykmelding.id
                ) else null,
                lestSoknad = false,
                lestSykmelding = false,
            )
        }
    }
