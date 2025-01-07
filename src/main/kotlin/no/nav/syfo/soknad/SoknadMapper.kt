package no.nav.syfo.soknad

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.soknad.kafka.model.FlexSoknad
import no.nav.syfo.soknad.kafka.model.FlexSoknadStatus
import no.nav.syfo.soknad.kafka.model.FlexSoknadsperiode
import no.nav.syfo.soknad.kafka.model.FlexSporsmal
import no.nav.syfo.soknad.kafka.model.FlexSvar
import no.nav.syfo.soknad.kafka.model.FlexSvartype
import no.nav.syfo.soknad.kafka.model.FlexSykmeldingstype
import no.nav.syfo.soknad.kafka.model.FlexVisningskriterium
import no.nav.syfo.soknad.model.Soknad
import no.nav.syfo.soknad.model.SoknadStatus
import no.nav.syfo.soknad.model.Soknadsperiode
import no.nav.syfo.soknad.model.Sporsmal
import no.nav.syfo.soknad.model.Svar
import no.nav.syfo.soknad.model.Svartype
import no.nav.syfo.soknad.model.Sykmeldingstype
import no.nav.syfo.soknad.model.Visningskriterium

fun FlexSoknad.toSoknad(): Soknad {
    return Soknad(
        id = id,
        fnr = fnr,
        orgnummer = arbeidsgiver?.orgnummer,
        status = getStatus(status),
        sykmeldingId = sykmeldingId,
        korrigertAv = korrigertAv,
        korrigerer = korrigerer,
        sendtArbeidsgiver = sendtArbeidsgiver,
        fom = fom,
        tom = tom,
        sendtNav = sendtNav,
        sporsmal = sporsmal?.filter { it.erWhitelistetForArbeidsgiver() }?.map { it.toSporsmal() }
                ?: emptyList(),
        soknadsperioder = soknadsperioder.toSoknadsperioder(),
    )
}

private fun List<FlexSoknadsperiode>?.toSoknadsperioder(): List<Soknadsperiode> {
    if (this == null) {
        throw IllegalStateException("soknadsperioder is null")
    } else {
        return this.map { it.toSoknadsperiode() }
    }
}

private fun FlexSoknadsperiode.toSoknadsperiode(): Soknadsperiode {
    return Soknadsperiode(
        fom = fom,
        tom = tom,
        sykmeldingsgrad = sykmeldingsgrad,
        sykmeldingstype = sykmeldingstype(),
    )
}

private fun FlexSoknadsperiode.sykmeldingstype() =
    when (sykmeldingstype) {
        FlexSykmeldingstype.AKTIVITET_IKKE_MULIG -> Sykmeldingstype.AKTIVITET_IKKE_MULIG
        FlexSykmeldingstype.GRADERT -> Sykmeldingstype.GRADERT
        FlexSykmeldingstype.BEHANDLINGSDAGER -> Sykmeldingstype.BEHANDLINGSDAGER
        FlexSykmeldingstype.AVVENTENDE -> Sykmeldingstype.AVVENTENDE
        FlexSykmeldingstype.REISETILSKUDD -> Sykmeldingstype.REISETILSKUDD
        null -> throw IllegalStateException("sykmeldingstype is null")
    }

fun FlexSporsmal.toSporsmal(): Sporsmal {
    return Sporsmal(
        id = id,
        tag = tag,
        min = min,
        max = max,
        sporsmalstekst = sporsmalstekst,
        undertekst = undertekst,
        kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal.toVisningskriterium(),
        svartype = svartype.toSvarType(),
        svar = svar.toSvarList(),
        undersporsmal = undersporsmal?.map { it.toSporsmal() } ?: emptyList(),
    )
}

private fun List<FlexSvar>?.toSvarList(): List<Svar> {
    if (this == null) {
        throw IllegalStateException("Svar list is null")
    } else {
        return this.map { it.toSvar() }
    }
}

private fun FlexSvar.toSvar(): Svar {
    return Svar(verdi ?: throw IllegalStateException("svar.verdi is null"))
}

private fun FlexSvartype?.toSvarType(): Svartype {
    return when (this) {
        FlexSvartype.JA_NEI -> Svartype.JA_NEI
        FlexSvartype.CHECKBOX -> Svartype.CHECKBOX
        FlexSvartype.CHECKBOX_GRUPPE -> Svartype.CHECKBOX_GRUPPE
        FlexSvartype.CHECKBOX_PANEL -> Svartype.CHECKBOX_PANEL
        FlexSvartype.DATO -> Svartype.DATO
        FlexSvartype.PERIODE -> Svartype.PERIODE
        FlexSvartype.PERIODER -> Svartype.PERIODER
        FlexSvartype.TIMER -> Svartype.TIMER
        FlexSvartype.FRITEKST -> Svartype.FRITEKST
        FlexSvartype.IKKE_RELEVANT -> Svartype.IKKE_RELEVANT
        FlexSvartype.GRUPPE_AV_UNDERSPORSMAL -> Svartype.GRUPPE_AV_UNDERSPORSMAL
        FlexSvartype.BEKREFTELSESPUNKTER -> Svartype.BEKREFTELSESPUNKTER
        FlexSvartype.OPPSUMMERING -> Svartype.OPPSUMMERING
        FlexSvartype.PROSENT -> Svartype.PROSENT
        FlexSvartype.RADIO_GRUPPE -> Svartype.RADIO_GRUPPE
        FlexSvartype.RADIO_GRUPPE_TIMER_PROSENT -> Svartype.RADIO_GRUPPE_TIMER_PROSENT
        FlexSvartype.RADIO -> Svartype.RADIO
        FlexSvartype.TALL -> Svartype.TALL
        FlexSvartype.RADIO_GRUPPE_UKEKALENDER -> Svartype.RADIO_GRUPPE_UKEKALENDER
        FlexSvartype.LAND -> Svartype.LAND
        FlexSvartype.COMBOBOX_SINGLE -> Svartype.COMBOBOX_SINGLE
        FlexSvartype.COMBOBOX_MULTI -> Svartype.COMBOBOX_MULTI
        FlexSvartype.INFO_BEHANDLINGSDAGER -> Svartype.INFO_BEHANDLINGSDAGER
        FlexSvartype.KVITTERING -> Svartype.KVITTERING
        FlexSvartype.DATOER -> Svartype.DATOER
        FlexSvartype.BELOP -> Svartype.BELOP
        FlexSvartype.KILOMETER -> Svartype.KILOMETER
        null -> throw IllegalStateException("Svartype is null")
    }
}

private fun FlexVisningskriterium?.toVisningskriterium(): Visningskriterium? {
    return when (this) {
        FlexVisningskriterium.NEI -> Visningskriterium.NEI
        FlexVisningskriterium.JA -> Visningskriterium.JA
        FlexVisningskriterium.CHECKED -> Visningskriterium.CHECKED
        null -> null
    }
}

fun getStatus(status: FlexSoknadStatus): SoknadStatus {
    return when (status) {
        FlexSoknadStatus.NY -> SoknadStatus.NY
        FlexSoknadStatus.SENDT -> SoknadStatus.SENDT
        FlexSoknadStatus.FREMTIDIG -> SoknadStatus.FREMTIDIG
        FlexSoknadStatus.KORRIGERT -> SoknadStatus.KORRIGERT
        FlexSoknadStatus.AVBRUTT -> SoknadStatus.AVBRUTT
        FlexSoknadStatus.SLETTET -> SoknadStatus.SLETTET
        FlexSoknadStatus.UTGAATT -> SoknadStatus.UTGAATT
    }
}

fun FlexSoknad.toSoknadDbModel(): SoknadDbModel {
    return SoknadDbModel(
        soknadId = id,
        sykmeldingId = sykmeldingId,
        pasientFnr = fnr,
        orgnummer = arbeidsgiver?.orgnummer
                ?: throw IllegalStateException("Har mottatt sendt søknad uten orgnummer: $id"),
        sendtDato = sendtArbeidsgiver?.toLocalDate(),
        lest = false,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
        tom = tom!!,
        sykepengesoknad = toSoknad(),
    )
}
