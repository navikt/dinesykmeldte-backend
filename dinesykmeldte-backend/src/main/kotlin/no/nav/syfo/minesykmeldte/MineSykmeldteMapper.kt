package no.nav.syfo.minesykmeldte

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SporsmalDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.minesykmeldte.model.Hendelse
import no.nav.syfo.minesykmeldte.model.HendelseType
import no.nav.syfo.minesykmeldte.model.PeriodeEnum
import no.nav.syfo.minesykmeldte.model.PreviewFremtidigSoknad
import no.nav.syfo.minesykmeldte.model.PreviewNySoknad
import no.nav.syfo.minesykmeldte.model.PreviewSendtSoknad
import no.nav.syfo.minesykmeldte.model.PreviewSoknad
import no.nav.syfo.minesykmeldte.model.Soknadsperiode
import no.nav.syfo.minesykmeldte.model.Sporsmal
import no.nav.syfo.minesykmeldte.model.Svar
import no.nav.syfo.minesykmeldte.model.Undersporsmal
import java.util.Collections.max

class MineSykmeldteMapper private constructor() {
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
                sykmeldingId = soknad.sykmeldingId,
                fom = soknad.fom,
                tom = soknad.tom,
                lest = lest,
                korrigererSoknadId = soknad.korrigerer,
                sendtDato = soknad.sendtArbeidsgiver
                    ?: throw IllegalStateException("sendtArbeidsgiver is null for soknad: ${soknad.id}"),
                perioder = soknad.soknadsperioder?.map { it.toSoknadsperiode() }
                    ?: throw IllegalStateException("søknadsperioder must not be null in sendt soknad: ${soknad.id}"),
            )

        private fun getFremtidigSoknad(soknad: SykepengesoknadDTO): PreviewFremtidigSoknad =
            PreviewFremtidigSoknad(
                id = soknad.id,
                sykmeldingId = soknad.sykmeldingId,
                fom = soknad.fom,
                tom = soknad.tom,
                perioder = soknad.soknadsperioder?.map { it.toSoknadsperiode() }
                    ?: throw IllegalStateException("søknadsperioder must not be null in fremtidig soknad: ${soknad.id}"),
            )

        private fun getNySoknad(soknad: SykepengesoknadDTO, lest: Boolean, hendelser: List<Hendelse>): PreviewNySoknad =
            PreviewNySoknad(
                varsel = !lest,
                id = soknad.id,
                sykmeldingId = soknad.sykmeldingId,
                fom = soknad.fom,
                tom = soknad.tom,
                perioder = soknad.soknadsperioder?.map { it.toSoknadsperiode() }
                    ?: throw IllegalStateException("søknadsperioder must not be null in ny soknad: ${soknad.id}"),
                ikkeSendtSoknadVarsel = hendelser.any { it.id == soknad.id && it.oppgavetype == HendelseType.IKKE_SENDT_SOKNAD }
            )

        fun SoknadsperiodeDTO.toSoknadsperiode(): Soknadsperiode = Soknadsperiode(
            fom = requireNotNull(fom),
            tom = requireNotNull(tom),
            sykmeldingsgrad = sykmeldingsgrad,
            sykmeldingstype = PeriodeEnum.valueOf(sykmeldingstype.toString()),
        )

        private fun SporsmalDTO.toUndersporsmal(): Undersporsmal = Undersporsmal(
            id = requireNotNull(id),
            tag = requireNotNull(tag),
            min = min,
            max = max,
            sporsmalstekst = sporsmalstekst,
            undertekst = undertekst,
            svartype = requireNotNull(svartype),
            kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal,
            svar = svar?.map {
                Svar(
                    verdi = requireNotNull(it.verdi),
                )
            },
            undersporsmal = undersporsmal?.map { it.toUndersporsmal() },
        )

        fun SporsmalDTO.toSporsmal(): Sporsmal = Sporsmal(
            id = requireNotNull(id),
            tag = requireNotNull(tag),
            min = min,
            max = max,
            sporsmalstekst = requireNotNull(sporsmalstekst),
            undertekst = undertekst,
            svartype = requireNotNull(svartype),
            kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal,
            svar = svar?.map {
                Svar(
                    verdi = requireNotNull(it.verdi),
                )
            },
            undersporsmal = undersporsmal?.map { it.toUndersporsmal() },
        )
    }
}
