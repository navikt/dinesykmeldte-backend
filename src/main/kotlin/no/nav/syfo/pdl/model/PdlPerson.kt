package no.nav.syfo.pdl.model

import no.nav.syfo.util.toFormattedNameString

data class PdlPerson(
    val navn: Navn,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

fun Navn.formatName(): String = toFormattedNameString(fornavn, mellomnavn, etternavn)
