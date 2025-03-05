package no.nav.syfo.dinesykmeldte.model

data class MulighetForArbeid(
    val aktivitetIkkeMulig434: List<String>,
    val aarsakAktivitetIkkeMulig434: String,
    val perioder: List<Periode>,
)
