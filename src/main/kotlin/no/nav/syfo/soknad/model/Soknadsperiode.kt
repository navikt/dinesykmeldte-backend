package no.nav.syfo.soknad.model

import java.time.LocalDate

data class Soknadsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val sykmeldingsgrad: Int?,
    val sykmeldingstype: Sykmeldingstype,
)
