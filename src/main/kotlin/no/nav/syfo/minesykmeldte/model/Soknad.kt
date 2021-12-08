package no.nav.syfo.minesykmeldte.model

import no.nav.syfo.kafka.felles.SoknadsstatusDTO
import no.nav.syfo.kafka.felles.SoknadstypeDTO
import java.time.LocalDate

data class Soknad(
    val id: String,
    val sykmeldingId: String,
    val navn: String,
    val fnr: String,
    val lest: Boolean,
    val orgnummer: String,
    val sendtDato: LocalDate?,
    val tom: LocalDate,
    val details: SoknadDetails,
)

data class SoknadDetails(
    val type: SoknadstypeDTO,
    val status: SoknadsstatusDTO,
)
