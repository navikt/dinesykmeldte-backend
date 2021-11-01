package no.nav.syfo.minesykmeldte.model

import no.nav.syfo.kafka.felles.SykepengesoknadDTO
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

data class Soknad(
    val id: String,
    val sykmeldingId: String?,
    val fom: LocalDate?,
    val tom: LocalDate,
    val status: String,
    val sendtDato: LocalDate,
    val soknadDTO: SykepengesoknadDTO,
    val lest: Boolean
)
