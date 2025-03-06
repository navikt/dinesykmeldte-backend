package no.nav.syfo.dinesykmeldte.model

data class DineSykmeldteSykmelding(
    val sykmeldingId: String,
    val pasient: Pasient,
    val mulighetForArbeid: MulighetForArbeid,
    val skalViseSkravertFelt: Boolean = true,
    val friskmelding: Friskmelding,
    val arbeidsgiver: String?,
    val bekreftelse: Bekreftelse,
    val arbeidsevne: Arbeidsevne,
    val innspillTilArbeidsgiver: String?,
)
