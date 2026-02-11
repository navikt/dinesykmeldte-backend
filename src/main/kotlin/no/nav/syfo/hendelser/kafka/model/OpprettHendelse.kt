package no.nav.syfo.hendelser.kafka.model

import java.time.OffsetDateTime

data class OpprettHendelse(
    val ansattFnr: String,
    val orgnummer: String,
    val oppgavetype: String, // f.eks. "IKKE_SENDT_SOKNAD", "INNKALLING_DIALOGMOTE", "REVIDERT_OPPFOLGINGSPLAN"
    val lenke: String?,
    val tekst: String?,
    val timestamp: OffsetDateTime,
    val utlopstidspunkt: OffsetDateTime?,
)
