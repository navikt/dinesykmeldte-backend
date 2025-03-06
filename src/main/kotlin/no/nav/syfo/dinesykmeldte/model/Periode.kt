package no.nav.syfo.dinesykmeldte.model

import java.time.LocalDate

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int?,
    val behandlingsdager: Int?,
    val reisetilskudd: Boolean,
    val avventende: String?,
)
