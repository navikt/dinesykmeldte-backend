package no.nav.syfo.dinesykmeldte.service

import no.nav.syfo.dinesykmeldte.model.Ansatt
import no.nav.syfo.dinesykmeldte.model.Sykmeldt
import no.nav.syfo.dinesykmeldte.util.isActive
import no.nav.syfo.dinesykmeldte.util.toDineSykmeldteSykmelding
import no.nav.syfo.minesykmeldte.db.MinSykmeldtDbModel
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb

class DineSykmeldteService(
    private val sykmeldingDb: MineSykmeldteDb,
) {
    suspend fun getSykmeldt(narmestelederId: String, fnr: String): Sykmeldt? {
        val dineSykmeldte =
            toDineSykmeldte(
                sykmeldingDb.getMineSykmeldteWithoutSoknad(
                    lederFnr = fnr,
                    narmestelederId = narmestelederId
                )
            )
        if (dineSykmeldte.size > 1) {
            throw RuntimeException("Fant flere sykmeldte med samme narmestelederId")
        }
        return dineSykmeldte.singleOrNull()
    }

    suspend fun getDineSykmeldte(fnr: String): List<Sykmeldt> {
        return toDineSykmeldte(
            sykmeldingDb.getMineSykmeldteWithoutSoknad(lederFnr = fnr, narmestelederId = null)
        )
    }

    private fun toDineSykmeldte(sykmeldinger: List<MinSykmeldtDbModel>) =
        sykmeldinger
            .groupBy {
                Ansatt(
                    fnr = it.sykmeldtFnr,
                    navn = it.sykmeldtNavn,
                    orgnummer = it.orgnummer,
                    narmestelederId = it.narmestelederId,
                )
            }
            .map { ansatt ->
                Sykmeldt(
                    narmestelederId = ansatt.key.narmestelederId,
                    orgnummer = ansatt.key.orgnummer,
                    fnr = ansatt.key.fnr,
                    ansatt.key.navn,
                    sykmeldinger = ansatt.value.map { it.toDineSykmeldteSykmelding(ansatt.key) },
                    aktivSykmelding =
                        ansatt.value.any { it.sykmelding.sykmeldingsperioder.isActive() },
                    sistSykmeldtTom = ansatt.value.maxOfOrNull { it.sykmelding.sykmeldingsperioder.maxOf { periode -> periode.tom } }
                )
            }
}
