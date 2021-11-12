package no.nav.syfo.minesykmeldte

import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.minesykmeldte.db.SykmeldtDbModel
import no.nav.syfo.minesykmeldte.model.PreviewSoknad
import no.nav.syfo.minesykmeldte.model.PreviewSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.time.LocalDate

class MineSykmeldteMapper private constructor() {
    companion object {
        fun toPreviewSykmelding(sykmeldingDbModel: SykmeldtDbModel): PreviewSykmelding {
            return PreviewSykmelding(
                id = sykmeldingDbModel.sykmeldingId.toString(),
                fom = sykmeldingDbModel.sykmelding.sykmeldingsperioder.minOf { it.fom },
                tom = sykmeldingDbModel.sykmelding.sykmeldingsperioder.maxOf { it.tom },
                type = getTypeSykmelding(sykmeldingDbModel.sykmelding),
                lest = sykmeldingDbModel.lestSykmelding
            )
        }

        fun toPreviewSoknad(soknad: SykepengesoknadDTO, lest: Boolean): PreviewSoknad {
            return PreviewSoknad(
                id = soknad.id,
                sykmeldingId = soknad.sykmeldingId,
                fom = soknad.fom,
                tom = soknad.tom,
                status = soknad.status.name,
                sendtDato = soknad.sendtArbeidsgiver?.toLocalDate(),
                lest = lest
            )
        }

        private fun getTypeSykmelding(sykmelding: ArbeidsgiverSykmelding): String {
            val now = LocalDate.now()
            val sortedPeriods = sykmelding.sykmeldingsperioder.sortedBy { it.fom }

            return when (val periodNearestNow = sortedPeriods.find { it.tom >= now }) {
                null -> formatPeriodType(sortedPeriods.last())
                else -> formatPeriodType(periodNearestNow)
            }
        }

        private fun formatPeriodType(relevantPeriod: SykmeldingsperiodeAGDTO) =
            when (relevantPeriod.type) {
                PeriodetypeDTO.GRADERT -> relevantPeriod.gradert.gradPercent
                PeriodetypeDTO.AKTIVITET_IKKE_MULIG -> "100%"
                PeriodetypeDTO.AVVENTENDE -> "Avventende"
                PeriodetypeDTO.BEHANDLINGSDAGER -> "Behandlingsdager"
                PeriodetypeDTO.REISETILSKUDD -> "Reisetilskudd"
            }
    }
}

private val GradertDTO?.gradPercent: String
    get() = this?.let { "${this.grad}%" } ?: "Ukjent gradering"
