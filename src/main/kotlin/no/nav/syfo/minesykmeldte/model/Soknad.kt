package no.nav.syfo.minesykmeldte.model

import no.nav.syfo.kafka.felles.FravarstypeDTO
import java.time.LocalDate

data class Soknad(
    val id: String,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val navn: String,
    val fnr: String,
    val korrigertBySoknadId: String?,
    val fravar: List<Fravar>,
    // TODO: tilbake i fullt arbeid?
)

data class Fravar(
    val fom: LocalDate,
    val tom: LocalDate,
    val type: FravarstypeDTO,
)
