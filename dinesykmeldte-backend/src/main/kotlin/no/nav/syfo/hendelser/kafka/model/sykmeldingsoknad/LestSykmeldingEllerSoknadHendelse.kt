package no.nav.syfo.hendelser.kafka.model.sykmeldingsoknad

data class LestSykmeldingEllerSoknadHendelse(
    val id: String,
    val opprettHendelse: OpprettLestSykmeldingEllerSoknadHendelse?,
    val ferdigstillHendelse: FerdigstillLestSykmeldingEllerSoknadHendelse?
)
