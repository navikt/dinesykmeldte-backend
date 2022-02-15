package no.nav.syfo.hendelser.kafka.model.sykmeldingsoknad

import java.time.OffsetDateTime

data class FerdigstillLestSykmeldingEllerSoknadHendelse(
    val id: String,
    val timestamp: OffsetDateTime,
    val oppgavetype: String?
)
