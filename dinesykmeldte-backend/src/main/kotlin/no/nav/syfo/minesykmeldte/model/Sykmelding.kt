package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate
import java.time.OffsetDateTime

data class Sykmelding(
    val id: String,
    val startdatoSykefravar: LocalDate,
    val kontaktDato: LocalDate?,
    val navn: String,
    val fnr: String,
    val lest: Boolean,
    val behandletTidspunkt: LocalDate,
    val arbeidsgiver: Arbeidsgiver,
    val perioder: List<Periode>,
    val arbeidsforEtterPeriode: Boolean?,
    val hensynArbeidsplassen: String?,
    val tiltakArbeidsplassen: String?,
    val innspillArbeidsplassen: String?,
    val behandler: Behandler?,
    val sendtTilArbeidsgiverDato: OffsetDateTime?,
    val utenlandskSykmelding: UtenlandskSykmelding?,
    val egenmeldingsdager: List<LocalDate>?,
)

data class Arbeidsgiver(
    val navn: String?,
)

data class Behandler(
    val navn: String,
    val hprNummer: String?,
    val telefon: String?,
)

enum class PeriodeEnum {
    AKTIVITET_IKKE_MULIG,
    AVVENTENDE,
    BEHANDLINGSDAGER,
    GRADERT,
    REISETILSKUDD,
}

interface Periode {
    val fom: LocalDate
    val tom: LocalDate
    val type: PeriodeEnum
}

data class AktivitetIkkeMulig(
    override val fom: LocalDate,
    override val tom: LocalDate,
    val arbeidsrelatertArsak: ArbeidsrelatertArsak?,
) : Periode {
    override val type = PeriodeEnum.AKTIVITET_IKKE_MULIG
}

data class Gradert(
    override val fom: LocalDate,
    override val tom: LocalDate,
    val grad: Int,
    val reisetilskudd: Boolean,
) : Periode {
    override val type = PeriodeEnum.GRADERT
}

data class Behandlingsdager(
    override val fom: LocalDate,
    override val tom: LocalDate,
    val behandlingsdager: Int,
) : Periode {
    override val type = PeriodeEnum.BEHANDLINGSDAGER
}

data class Reisetilskudd(
    override val fom: LocalDate,
    override val tom: LocalDate,
) : Periode {
    override val type = PeriodeEnum.REISETILSKUDD
}

data class Avventende(
    override val fom: LocalDate,
    override val tom: LocalDate,
    val tilrettelegging: String?,
) : Periode {
    override val type = PeriodeEnum.AVVENTENDE
}

enum class ArbeidsrelatertArsakEnum {
    MANGLENDE_TILRETTELEGGING,
    ANNET,
}

data class ArbeidsrelatertArsak(
    val arsak: List<ArbeidsrelatertArsakEnum>,
    val beskrivelse: String?,
)

data class UtenlandskSykmelding(
    val land: String,
)
