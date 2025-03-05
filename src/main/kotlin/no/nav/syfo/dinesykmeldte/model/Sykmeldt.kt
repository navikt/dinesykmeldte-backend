package no.nav.syfo.dinesykmeldte.model

data class Sykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String?,
    val sykmeldinger: List<DineSykmeldteSykmelding>?,
    val aktivSykmelding: Boolean?,
)
