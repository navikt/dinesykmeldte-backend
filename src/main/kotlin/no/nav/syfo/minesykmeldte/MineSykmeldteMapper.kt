package no.nav.syfo.minesykmeldte

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
import no.nav.syfo.soknad.model.Soknad
import no.nav.syfo.soknad.model.SoknadStatus

class MineSykmeldteMapper private constructor() {
    companion object {
        fun toPreviewSoknad(
            soknad: Soknad,
            lest: Boolean,
            hendelser: List<Hendelse>,
        ): PreviewSoknad =
            when (soknad.status) {
                SoknadStatus.NY -> getNySoknad(soknad, lest, hendelser)
                SoknadStatus.SENDT -> getSendtSoknad(soknad, lest)
                SoknadStatus.FREMTIDIG -> getFremtidigSoknad(soknad)
                else -> throw IllegalArgumentException("Incorrect soknad status ${soknad.status}")
            }

        private fun getSendtSoknad(soknad: Soknad, lest: Boolean): PreviewSendtSoknad =
            PreviewSendtSoknad(
                id = soknad.id,
                sykmeldingId = soknad.sykmeldingId,
                fom = soknad.fom,
                tom = soknad.tom,
                lest = lest,
                korrigererSoknadId = soknad.korrigerer,
                sendtDato = soknad.sendtArbeidsgiver
                        ?: throw IllegalStateException(
                            "sendtArbeidsgiver is null for soknad: ${soknad.id}"
                        ),
                perioder = soknad.soknadsperioder.map { it.toSoknadsperiode() }
            )

        private fun getFremtidigSoknad(soknad: Soknad): PreviewFremtidigSoknad =
            PreviewFremtidigSoknad(
                id = soknad.id,
                sykmeldingId = soknad.sykmeldingId,
                fom = soknad.fom,
                tom = soknad.tom,
                perioder = soknad.soknadsperioder.map { it.toSoknadsperiode() }
            )

        private fun getNySoknad(
            soknad: Soknad,
            lest: Boolean,
            hendelser: List<Hendelse>
        ): PreviewNySoknad =
            PreviewNySoknad(
                lest = lest,
                id = soknad.id,
                sykmeldingId = soknad.sykmeldingId,
                fom = soknad.fom,
                tom = soknad.tom,
                perioder = soknad.soknadsperioder.map { it.toSoknadsperiode() },
                ikkeSendtSoknadVarsel =
                    hendelser.any {
                        it.id == soknad.id && it.oppgavetype == HendelseType.IKKE_SENDT_SOKNAD
                    },
                ikkeSendtSoknadVarsletDato =
                    hendelser
                        .find {
                            it.id == soknad.id && it.oppgavetype == HendelseType.IKKE_SENDT_SOKNAD
                        }
                        ?.mottatt,
            )

        fun no.nav.syfo.soknad.model.Soknadsperiode.toSoknadsperiode(): Soknadsperiode =
            Soknadsperiode(
                fom = fom,
                tom = tom,
                sykmeldingsgrad = sykmeldingsgrad,
                sykmeldingstype = PeriodeEnum.valueOf(sykmeldingstype.toString()),
            )

        private fun no.nav.syfo.soknad.model.Sporsmal.toUndersporsmal(): Undersporsmal =
            Undersporsmal(
                id = id,
                tag = tag,
                min = min,
                max = max,
                sporsmalstekst = sporsmalstekst,
                undertekst = undertekst,
                svartype = svartype,
                kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal,
                svar =
                    svar.map {
                        Svar(
                            verdi = requireNotNull(it.verdi),
                        )
                    },
                undersporsmal = undersporsmal.map { it.toUndersporsmal() },
            )

        fun no.nav.syfo.soknad.model.Sporsmal.toSporsmal(): Sporsmal =
            Sporsmal(
                id = id,
                tag = tag,
                min = min,
                max = max,
                sporsmalstekst = requireNotNull(sporsmalstekst),
                undertekst = undertekst,
                svartype = svartype,
                kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal,
                svar =
                    svar.map {
                        Svar(
                            verdi = requireNotNull(it.verdi),
                        )
                    },
                undersporsmal = undersporsmal.map { it.toUndersporsmal() },
            )
    }
}
