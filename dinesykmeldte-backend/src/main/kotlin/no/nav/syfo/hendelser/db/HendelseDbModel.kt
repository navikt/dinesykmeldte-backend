package no.nav.syfo.hendelser.db

import java.time.OffsetDateTime
import java.util.UUID

data class HendelseDbModel(
    val id: String,
    val hendelseId: UUID,
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
