package no.nav.syfo.pdl.model

import no.nav.syfo.util.toFormattedNameString

data class PdlPerson(
    val navn: Navn,
    val gtType: String?,
    val gtLand: String?,
    val gtKommune: String?,
    val gtBydel: String?,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

fun Navn.formatName(): String {
    return toFormattedNameString(fornavn, mellomnavn, etternavn)
}
