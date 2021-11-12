package no.nav.syfo.minesykmeldte

import no.nav.syfo.minesykmeldte.MineSykmeldteMapper.Companion.toPreviewSoknad
import no.nav.syfo.minesykmeldte.MineSykmeldteMapper.Companion.toPreviewSykmelding
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.db.SykmeldtDbModel
import no.nav.syfo.minesykmeldte.model.AktivitetIkkeMulig
import no.nav.syfo.minesykmeldte.model.Arbeidsgiver
import no.nav.syfo.minesykmeldte.model.ArbeidsrelatertArsak
import no.nav.syfo.minesykmeldte.model.ArbeidsrelatertArsakEnum
import no.nav.syfo.minesykmeldte.model.Avventende
import no.nav.syfo.minesykmeldte.model.Behandler
import no.nav.syfo.minesykmeldte.model.MinSykmeldtKey
import no.nav.syfo.minesykmeldte.model.Periode
import no.nav.syfo.minesykmeldte.model.PreviewSykmeldt
import no.nav.syfo.minesykmeldte.model.Sykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.util.toFormattedNameString
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class MineSykmeldteService(private val mineSykmeldteDb: MineSykmeldteDb) {
    fun getMineSykmeldte(lederFnr: String): List<PreviewSykmeldt> =
        mineSykmeldteDb.getMineSykmeldte(lederFnr).groupBy { it.toMinSykmeldtKey() }.map { it ->
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

    fun getSykmelding(sykmeldingId: UUID, lederFnr: String): Sykmelding? {
        return mineSykmeldteDb.getSykmelding(sykmeldingId, lederFnr)?.toSykmelding()
    }
}

private fun isFriskmeldt(it: Map.Entry<MinSykmeldtKey, List<SykmeldtDbModel>>): Boolean {
    val latestTom: LocalDate = it.value
        .flatMap { it.sykmelding.sykmeldingsperioder }
        .maxOf { it.tom }

    return ChronoUnit.DAYS.between(latestTom, LocalDate.now()) > 16
}

private fun mapNullableSoknad(sykmeldtDbModel: SykmeldtDbModel) =
    sykmeldtDbModel.soknad?.let { toPreviewSoknad(it, sykmeldtDbModel.lestSoknad) }

private fun SykmeldtDbModel.toMinSykmeldtKey(): MinSykmeldtKey = MinSykmeldtKey(
    narmestelederId = this.narmestelederId,
    orgnummer = this.orgnummer,
    navn = this.sykmeldtNavn,
    fnr = this.sykmeldtFnr,
    startDatoSykefravaer = this.startDatoSykefravar,
)

private fun SykmeldingDbModel.toSykmelding(): Sykmelding = Sykmelding(
    sykmeldingId = this.sykmeldingId,
    kontaktDato = this.sykmelding.kontaktMedPasient.kontaktDato,
    navn = this.pasientNavn,
    fnr = this.pasientFnr,
    lest = this.lest,
    arbeidsgiver = Arbeidsgiver(
        navn = this.orgnavn,
        orgnummer = this.orgnummer,
        yrke = this.sykmelding.arbeidsgiver.yrkesbetegnelse
    ),
    perioder = this.sykmelding.sykmeldingsperioder.map { it.toSykmeldingPeriode() },
    arbeidsforEtterPeriode = this.sykmelding.prognose?.arbeidsforEtterPeriode,
    hensynArbeidsplassen = this.sykmelding.prognose?.hensynArbeidsplassen,
    tiltakArbeidsplassen = this.sykmelding.tiltakArbeidsplassen,
    innspillArbeidsplassen = this.sykmelding.meldingTilArbeidsgiver,
    behandler = this.sykmelding.behandler.let {
        Behandler(
            navn = it.formatName(),
            hprNummer = it.hpr,
            telefon = it.tlf,
        )
    },
    startdatoSykefravar = TODO()
)

private fun SykmeldingsperiodeAGDTO.toSykmeldingPeriode(): Periode =
    when (this.type) {
        PeriodetypeDTO.AKTIVITET_IKKE_MULIG -> AktivitetIkkeMulig(
            this.fom,
            this.tom,
            this.aktivitetIkkeMulig?.arbeidsrelatertArsak?.let {
                ArbeidsrelatertArsak(
                    beskrivelse = it.beskrivelse,
                    arsak = it.arsak.map { arsak ->
                        ArbeidsrelatertArsakEnum.valueOf(arsak.toString())
                    }
                )
            }
        )
        PeriodetypeDTO.AVVENTENDE -> Avventende(
            this.fom,
            this.tom,
            tilrettelegging = this.innspillTilArbeidsgiver,
        )
        PeriodetypeDTO.BEHANDLINGSDAGER -> TODO()
        PeriodetypeDTO.GRADERT -> TODO()
        PeriodetypeDTO.REISETILSKUDD -> TODO()
    }

private fun BehandlerAGDTO.formatName(): String = toFormattedNameString(fornavn, mellomnavn, etternavn)
