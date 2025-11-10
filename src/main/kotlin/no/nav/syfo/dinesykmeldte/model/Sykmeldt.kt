package no.nav.syfo.dinesykmeldte.model

import java.time.LocalDate

data class Sykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String,
    val sykmeldinger: List<DineSykmeldteSykmelding>?,
    val aktivSykmelding: Boolean?,
    val sistSykmeldtTom: LocalDate?,
)
