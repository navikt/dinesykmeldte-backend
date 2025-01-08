package no.nav.syfo.hendelser.kafka.model

data class DineSykmeldteHendelse(
    val id: String,
    val opprettHendelse: OpprettHendelse?,
    val ferdigstillHendelse: FerdigstillHendelse?,
)
