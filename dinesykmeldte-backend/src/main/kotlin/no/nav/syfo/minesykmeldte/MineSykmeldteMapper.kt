package no.nav.syfo.minesykmeldte

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsperiodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SporsmalDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.minesykmeldte.db.MinSykmeldtDbModel
import no.nav.syfo.minesykmeldte.model.Hendelse
import no.nav.syfo.minesykmeldte.model.HendelseType
import no.nav.syfo.minesykmeldte.model.PeriodeEnum
import no.nav.syfo.minesykmeldte.model.PreviewFremtidigSoknad
import no.nav.syfo.minesykmeldte.model.PreviewKorrigertSoknad
import no.nav.syfo.minesykmeldte.model.PreviewNySoknad
import no.nav.syfo.minesykmeldte.model.PreviewSendtSoknad
import no.nav.syfo.minesykmeldte.model.PreviewSoknad
import no.nav.syfo.minesykmeldte.model.PreviewSykmelding
import no.nav.syfo.minesykmeldte.model.Soknadsperiode
import no.nav.syfo.minesykmeldte.model.Sporsmal
import no.nav.syfo.minesykmeldte.model.Svar
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.time.LocalDate
import java.util.Collections.max

class MineSykmeldteMapper private constructor() {
    companion object {
        fun toPreviewSykmelding(sykmeldingDbModel: MinSykmeldtDbModel): PreviewSykmelding {
            return PreviewSykmelding(
                id = sykmeldingDbModel.sykmeldingId,
                fom = sykmeldingDbModel.sykmelding.sykmeldingsperioder.minOf { it.fom },
                tom = sykmeldingDbModel.sykmelding.sykmeldingsperioder.maxOf { it.tom },
                type = getTypeSykmelding(sykmeldingDbModel.sykmelding),
                lest = sykmeldingDbModel.lestSykmelding
            )
        }

        fun toPreviewSoknad(soknad: SykepengesoknadDTO, lest: Boolean, hendelser: List<Hendelse>): PreviewSoknad =
            when (soknad.status) {
                SoknadsstatusDTO.NY -> getNySoknad(soknad, lest, hendelser)
                SoknadsstatusDTO.SENDT -> getSendtSoknad(soknad, lest)
                SoknadsstatusDTO.FREMTIDIG -> getFremtidigSoknad(soknad)
                SoknadsstatusDTO.KORRIGERT -> getKorrigertSoknad(soknad)
                else -> throw IllegalArgumentException("Incorrect soknad status ${soknad.status}")
            }

        private fun getKorrigertSoknad(soknad: SykepengesoknadDTO): PreviewKorrigertSoknad =
            PreviewKorrigertSoknad(
                id = soknad.id,
                sykmeldingId = soknad.sykmeldingId,
                fom = soknad.fom,
                tom = soknad.tom,
                korrigererSoknadId = soknad.korrigerer
                    ?: throw IllegalStateException("korrigerer must not be null in korrigert soknad: ${soknad.id}"),
                korrigertBySoknadId = soknad.korrigertAv,
                perioder = soknad.soknadsperioder?.map { it.toSoknadsperiode() }
                    ?: throw IllegalStateException("søknadsperioder must not be null in korrigert soknad: ${soknad.id}"),
            )

        private fun getSendtSoknad(soknad: SykepengesoknadDTO, lest: Boolean): PreviewSendtSoknad =
            PreviewSendtSoknad(
                id = soknad.id,
                sykmeldingId = soknad.sykmeldingId,
                fom = soknad.fom,
                tom = soknad.tom,
                lest = lest,
                sendtDato = soknad.sendtArbeidsgiver
                    ?: throw IllegalStateException("sendtArbeidsgiver is null for soknad: ${soknad.id}"),
                korrigertBySoknadId = soknad.korrigertAv,
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

        private fun getTypeSykmelding(sykmelding: ArbeidsgiverSykmelding): String {
            val now = LocalDate.now()
            val sortedPeriods = sykmelding.sykmeldingsperioder.sortedBy { it.fom }

            return when (val periodNearestNow = sortedPeriods.find { it.tom >= now }) {
                null -> formatPeriodType(sortedPeriods.last())
                else -> formatPeriodType(periodNearestNow)
            }
        }

        fun SoknadsperiodeDTO.toSoknadsperiode(): Soknadsperiode = Soknadsperiode(
            fom = requireNotNull(fom),
            tom = requireNotNull(tom),
            sykmeldingsgrad = sykmeldingsgrad,
            sykmeldingstype = PeriodeEnum.valueOf(sykmeldingstype.toString()),
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
            undersporsmal = undersporsmal?.map { it.toSporsmal() },
        )

        private fun formatPeriodType(relevantPeriod: SykmeldingsperiodeAGDTO) = when (relevantPeriod.type) {
            PeriodetypeDTO.GRADERT -> relevantPeriod.gradert.gradPercent
            PeriodetypeDTO.AKTIVITET_IKKE_MULIG -> "100%"
            PeriodetypeDTO.AVVENTENDE -> "Avventende"
            PeriodetypeDTO.BEHANDLINGSDAGER -> "Behandlingsdager"
            PeriodetypeDTO.REISETILSKUDD -> "Reisetilskudd"
        }
    }
}

private fun maxDate(first: LocalDate?, second: LocalDate?): LocalDate =
    max(
        listOf(
            first ?: LocalDate.MIN,
            second ?: LocalDate.MIN
        )
    )

private val GradertDTO?.gradPercent: String
    get() = this?.let { "${this.grad}%" } ?: "Ukjent gradering"
