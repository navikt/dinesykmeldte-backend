package no.nav.syfo.minesykmeldte.model

data class Hendelse(
    val id: String,
    val oppgavetype: String,
    val lenke: String?,
    val tekst: String?,
    val ferdigstilt: Boolean,
)
