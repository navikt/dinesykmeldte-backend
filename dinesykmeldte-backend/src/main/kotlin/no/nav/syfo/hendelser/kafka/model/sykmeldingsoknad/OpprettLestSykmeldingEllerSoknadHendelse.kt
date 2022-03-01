package no.nav.syfo.hendelser.kafka.model.sykmeldingsoknad

import java.time.OffsetDateTime

data class OpprettLestSykmeldingEllerSoknadHendelse(
    val id: String,
    val ansattFnr: String?,
    val orgnummer: String?,
    val oppgavetype: String,
    val lenke: String?,
    val tekst: String?,
    val timestamp: OffsetDateTime,
    val utlopstidspunkt: OffsetDateTime?
)
