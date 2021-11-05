package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate

data class MinSykmeldtKey(
    val narmestelederId: String,
    val orgnummer: String,
    val navn: String,
    val fnr: String,
    val startDatoSykefravaer: LocalDate
)
