package no.nav.syfo.soknad.kafka.model

import java.time.LocalDate
import java.time.LocalDateTime

data class FlexSoknad(
    val id: String,
    val fnr: String,
    val arbeidsgiver: FlexArbeidsgiver?,
    val status: FlexSoknadStatus,
    val sykmeldingId: String?,
    val korrigerer: String?,
    val korrigertAv: String?,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val sendtNav: LocalDateTime?,
    val sendtArbeidsgiver: LocalDateTime?,
    val sporsmal: List<FlexSporsmal>?,
    val soknadsperioder: List<FlexSoknadsperiode>?
)

data class FlexSoknadsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val sykmeldingsgrad: Int?,
    val sykmeldingstype: FlexSykmeldingstype?
)

enum class FlexSykmeldingstype {
    AKTIVITET_IKKE_MULIG,
    GRADERT,
    BEHANDLINGSDAGER,
    AVVENTENDE,
    REISETILSKUDD
}

data class FlexSporsmal(
    val id: String,
    val tag: String,
    val min: String?,
    val max: String?,
    val sporsmalstekst: String?,
    val undertekst: String?,
    val kriterieForVisningAvUndersporsmal: FlexVisningskriterium?,
    val svartype: FlexSvartype?,
    val svar: List<FlexSvar>?,
    val undersporsmal: List<FlexSporsmal>?
)

enum class FlexVisningskriterium {
    NEI,
    JA,
    CHECKED
}

enum class FlexSvartype {
    JA_NEI,
    CHECKBOX,
    CHECKBOX_GRUPPE,
    CHECKBOX_PANEL,
    DATO,
    PERIODE,
    PERIODER,
    TIMER,
    FRITEKST,
    IKKE_RELEVANT,
    GRUPPE_AV_UNDERSPORSMAL,
    BEKREFTELSESPUNKTER,
    OPPSUMMERING,
    PROSENT,
    RADIO_GRUPPE,
    RADIO_GRUPPE_TIMER_PROSENT,
    RADIO,
    TALL,
    RADIO_GRUPPE_UKEKALENDER,
    LAND,
    COMBOBOX_SINGLE,
    COMBOBOX_MULTI,
    INFO_BEHANDLINGSDAGER,
    KVITTERING,
    DATOER,
    BELOP,
    KILOMETER
}

data class FlexSvar(val verdi: String?)

enum class FlexSoknadStatus {
    NY,
    SENDT,
    FREMTIDIG,
    KORRIGERT,
    AVBRUTT,
    SLETTET,
    UTGAATT,
}

data class FlexArbeidsgiver(val orgnummer: String)
