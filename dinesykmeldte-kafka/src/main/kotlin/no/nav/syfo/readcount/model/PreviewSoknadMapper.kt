package no.nav.syfo.readcount.model

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO

class PreviewSoknadMapper private constructor() {
    companion object {
        fun toPreviewSoknad(
            soknad: SykepengesoknadDTO,
            lest: Boolean,
            hendelser: List<Hendelse>
        ): PreviewSoknad =
            when (soknad.status) {
                SoknadsstatusDTO.NY -> getNySoknad(soknad, lest, hendelser)
                SoknadsstatusDTO.SENDT -> getSendtSoknad(soknad, lest)
                SoknadsstatusDTO.FREMTIDIG -> getFremtidigSoknad(soknad)
                else -> throw IllegalArgumentException("Incorrect soknad status ${soknad.status}")
            }

        private fun getSendtSoknad(soknad: SykepengesoknadDTO, lest: Boolean): PreviewSendtSoknad =
            PreviewSendtSoknad(
                id = soknad.id,
                lest = lest,
                korrigererSoknadId = soknad.korrigerer
            )

        private fun getFremtidigSoknad(soknad: SykepengesoknadDTO): PreviewFremtidigSoknad =
            PreviewFremtidigSoknad(
                id = soknad.id
            )

        private fun getNySoknad(soknad: SykepengesoknadDTO, lest: Boolean, hendelser: List<Hendelse>): PreviewNySoknad =
            PreviewNySoknad(
                lest = lest,
                id = soknad.id,
                ikkeSendtSoknadVarsel = hendelser.any { it.id == soknad.id && it.oppgavetype == HendelseType.IKKE_SENDT_SOKNAD },
            )
    }
}
