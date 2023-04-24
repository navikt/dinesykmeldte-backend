package no.nav.syfo.narmesteleder.db

data class NarmestelederDbModel(
    val narmestelederId: String,
    val pasientFnr: String,
    val lederFnr: String,
    val orgnummer: String,
)
