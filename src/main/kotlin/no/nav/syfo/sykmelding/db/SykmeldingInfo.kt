package no.nav.syfo.sykmelding.db

import java.time.LocalDate

data class SykmeldingInfo(
    val sykmeldingId: String,
    val latestTom: LocalDate,
    val fnr: String,
)
