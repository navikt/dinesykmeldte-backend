package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate

// Når er man friskmeldt? 16 dager etter siste tom?
data class Sykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String,
    val startdatoSykefravaer: LocalDate,
    val friskmeldt: Boolean,
    val previewSykmeldinger: List<PreviewSykmelding>,
    val soknader: List<Soknad>
)

// Type kan være "100%", "50%", "avventende", ++
data class PreviewSykmelding(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val type: String,
    val lest: Boolean
)

// siden søknaden visstnok har lite info kan vi kanskje hente hele søknaden med en gang?
data class Soknad(
    val id: String,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val status: String,
    val sendtDato: LocalDate?,
    val sporsmalOgSvar: List<String>? // her skal det være et spørsmål/svar-format, men hvordan det ser ut kan vi vente med til apiet er klart fra flex
)
