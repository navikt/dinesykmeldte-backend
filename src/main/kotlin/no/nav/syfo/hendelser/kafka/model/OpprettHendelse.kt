package no.nav.syfo.hendelser.kafka.model

import java.time.OffsetDateTime

data class OpprettHendelse(
    val id: String,
    val ansattFnr: String?,
    val orgnummer: String?,
    val oppgavetype: String,
    val lenke: String?,
    val tekst: String?,
    val timestamp: OffsetDateTime,
    val utlopstidspunkt: OffsetDateTime?
)
