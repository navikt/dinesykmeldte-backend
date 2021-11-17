package no.nav.syfo.hendelser.kafka.model

import java.time.OffsetDateTime

data class FerdigstillHendelse(
    val id: String,
    val timestamp: OffsetDateTime,
    val oppgavetype: String
)
