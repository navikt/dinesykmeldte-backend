package no.nav.syfo.dinesykmeldte.model

import java.time.LocalDate

data class Bekreftelse(
    val sykmelder: String,
    val utstedelsesdato: LocalDate,
    val sykmelderTlf: String?,
)
