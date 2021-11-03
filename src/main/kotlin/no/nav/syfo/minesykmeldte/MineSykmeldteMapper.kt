package no.nav.syfo.minesykmeldte

import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.minesykmeldte.db.SykmeldtDbModel
import no.nav.syfo.minesykmeldte.model.PreviewSykmelding
import no.nav.syfo.minesykmeldte.model.PreviewSoknad
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding

class MineSykmeldteMapper private constructor() {
    companion object {
        fun toPreviewSykmelding(sykmeldingDbModel: SykmeldtDbModel): PreviewSykmelding {
            return PreviewSykmelding(
                id = sykmeldingDbModel.sykmeldingId,
                fom = sykmeldingDbModel.sykmelding.sykmeldingsperioder.minOf { it.fom },
                tom = sykmeldingDbModel.sykmelding.sykmeldingsperioder.maxOf { it.tom },
                type = getTypeSykmelding(sykmeldingDbModel.sykmelding),
                lest = sykmeldingDbModel.lestSykmelding
            )
        }

        private fun getTypeSykmelding(sykmelding: ArbeidsgiverSykmelding): String {
            return "100%" // TODO fix[]
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
    }
}
