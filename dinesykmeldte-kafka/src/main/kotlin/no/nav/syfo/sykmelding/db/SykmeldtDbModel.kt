package no.nav.syfo.sykmelding.db

import java.time.LocalDate

data class SykmeldtDbModel(
    val pasientFnr: String,
    val pasientNavn: String,
    val startdatoSykefravaer: LocalDate,
    val latestTom: LocalDate,
    val sistOppdatert: LocalDate?,
)
