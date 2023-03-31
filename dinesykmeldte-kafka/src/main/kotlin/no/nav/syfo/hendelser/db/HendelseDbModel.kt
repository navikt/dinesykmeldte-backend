package no.nav.syfo.hendelser.db

import java.time.OffsetDateTime

data class HendelseDbModel(
    val id: String,
    val pasientFnr: String,
    val orgnummer: String,
    val oppgavetype: String,
    val lenke: String?,
    val tekst: String?,
    val timestamp: OffsetDateTime,
    val utlopstidspunkt: OffsetDateTime?,
    val ferdigstilt: Boolean,
    val ferdigstiltTimestamp: OffsetDateTime?,
)
