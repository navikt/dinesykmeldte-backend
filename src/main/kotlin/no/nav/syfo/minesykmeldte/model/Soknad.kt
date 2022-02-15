package no.nav.syfo.minesykmeldte.model

import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import java.time.LocalDate

data class Soknad(
    val id: String,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val navn: String,
    val fnr: String,
    val lest: Boolean,
    val korrigertBySoknadId: String?,
    val fravar: List<Fravar>,
    val perioder: List<Soknadsperiode>,
    // TODO: tilbake i fullt arbeid?
)

data class Fravar(
    val fom: LocalDate,
    val tom: LocalDate,
    val type: FravarstypeDTO,
)

data class Soknadsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val sykmeldingsgrad: Int?,
    val sykmeldingstype: PeriodeEnum,
)
