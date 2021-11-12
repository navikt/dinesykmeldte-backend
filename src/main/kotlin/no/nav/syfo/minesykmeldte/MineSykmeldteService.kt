package no.nav.syfo.minesykmeldte

import no.nav.syfo.minesykmeldte.MineSykmeldteMapper.Companion.toPreviewSoknad
import no.nav.syfo.minesykmeldte.MineSykmeldteMapper.Companion.toPreviewSykmelding
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.db.SykmeldtDbModel
import no.nav.syfo.minesykmeldte.model.MinSykmeldtKey
import no.nav.syfo.minesykmeldte.model.PreviewSykmeldt
import no.nav.syfo.minesykmeldte.model.Sykmelding
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MineSykmeldteService(private val mineSykmeldteDb: MineSykmeldteDb) {
    fun getMineSykmeldte(lederFnr: String): List<PreviewSykmeldt> =
        mineSykmeldteDb.getMineSykmeldte(lederFnr).groupBy { toMinSykmeldtKey(it) }.map { it ->
            PreviewSykmeldt(
                narmestelederId = it.key.narmestelederId,
                orgnummer = it.key.orgnummer,
                fnr = it.key.fnr,
                navn = it.key.navn,
                startdatoSykefravar = it.key.startDatoSykefravaer,
                friskmeldt = isFriskmeldt(it),
                previewSykmeldinger = it.value.distinctBy { it.sykmeldingId }.map { sykmeldtDbModel ->
                    toPreviewSykmelding(sykmeldtDbModel)
                },
                previewSoknader = it.value.mapNotNull { mapNullableSoknad(it) }
            )
        }

    fun getSykmelding(sykmeldingId: String, lederFnr: String): Sykmelding {
        return mineSykmeldteDb.getSykmelding(sykmeldingId, lederFnr)
    }
    private fun isFriskmeldt(it: Map.Entry<MinSykmeldtKey, List<SykmeldtDbModel>>): Boolean {
        val latestTom: LocalDate = it.value
            .flatMap { it.sykmelding.sykmeldingsperioder }
            .maxOf { it.tom }

        return ChronoUnit.DAYS.between(latestTom, LocalDate.now()) > 16
    }

    private fun mapNullableSoknad(sykmeldtDbModel: SykmeldtDbModel) =
        sykmeldtDbModel.soknad?.let { toPreviewSoknad(it, sykmeldtDbModel.lestSoknad) }

    private fun toMinSykmeldtKey(sykmeldtDbModle: SykmeldtDbModel): MinSykmeldtKey = MinSykmeldtKey(
        narmestelederId = sykmeldtDbModle.narmestelederId,
        orgnummer = sykmeldtDbModle.orgnummer,
        navn = sykmeldtDbModle.sykmeldtNavn,
        fnr = sykmeldtDbModle.sykmeldtFnr,
        startDatoSykefravaer = sykmeldtDbModle.startDatoSykefravar,
    )


}
