package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate

data class PreviewSykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String,
    val startdatoSykefravar: LocalDate,
    val friskmeldt: Boolean,
    val previewSykmeldinger: List<PreviewSykmelding>,
    val previewSoknader: List<PreviewSoknad>
)

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
