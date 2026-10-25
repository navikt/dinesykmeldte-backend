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
    suspend fun getSykmeldt(
        narmestelederId: String,
        fnr: String,
    ): Sykmeldt? {
        val dineSykmeldte =
            toDineSykmeldte(
                sykmeldingDb.getMineSykmeldteWithoutSoknad(
                    lederFnr = fnr,
                    narmestelederId = narmestelederId,
                ),
            )
        if (dineSykmeldte.size > 1) {
            throw RuntimeException("Fant flere sykmeldte med samme narmestelederId")
        }
        return dineSykmeldte.singleOrNull()
    }

    suspend fun getDineSykmeldte(fnr: String): List<Sykmeldt> =
        toDineSykmeldte(
            sykmeldingDb.getMineSykmeldteWithoutSoknad(lederFnr = fnr, narmestelederId = null),
        )

    private fun toDineSykmeldte(sykmeldinger: List<MinSykmeldtDbModel>) =
        sykmeldinger
            .groupBy { it.narmestelederId }
            .map { (narmestelederId, documents) ->
                // SQL selects one sykmeldt row per relation, independent of the document ident.
                // The relation ID, not person metadata, is the grouping key.
                val relation = documents.first()
                val ansatt =
                    Ansatt(
                        fnr = relation.sykmeldtFnr,
                        navn = relation.sykmeldtNavn,
                        orgnummer = relation.orgnummer,
                        narmestelederId = narmestelederId,
                    )
                Sykmeldt(
                    narmestelederId = narmestelederId,
                    orgnummer = ansatt.orgnummer,
                    fnr = ansatt.fnr,
                    navn = ansatt.navn,
                    sykmeldinger = documents.map { it.toDineSykmeldteSykmelding(ansatt) },
                    aktivSykmelding =
                        documents.any { it.sykmelding.sykmeldingsperioder.isActive() },
                )
            }
}
