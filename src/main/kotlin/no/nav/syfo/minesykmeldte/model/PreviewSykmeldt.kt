package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate

// Når er man friskmeldt? 16 dager etter siste tom?
data class PreviewSykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String,
    val startdatoSykefravaer: LocalDate,
    val friskmeldt: Boolean,
    val previewSykmeldinger: List<PreviewSykmelding>,
    val previewSoknader: List<PreviewSoknad>
)

// Type kan være "100%", "50%", "avventende", ++
data class PreviewSykmelding(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val type: String,
    val lest: Boolean
)

data class PreviewSoknad(
    val id: String,
    val sykmeldingId: String?,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val status: String,
    val sendtDato: LocalDate?,
    val lest: Boolean
)
