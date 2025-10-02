package no.nav.syfo.sykmelding.db

import java.time.LocalDate

data class SykmeldingInfo(
    val sykmeldingId: String,
    val latestTom: LocalDate,
    val fnr: String,
)

data class ExtendedSykmeldtDbModel (
    val sykmeldingId: String,
    val pasientFnr: String,
    val orgnummer: String,
    val orgNavn: String,
    val fomDate: LocalDate,
    val latestTom: LocalDate,
)
