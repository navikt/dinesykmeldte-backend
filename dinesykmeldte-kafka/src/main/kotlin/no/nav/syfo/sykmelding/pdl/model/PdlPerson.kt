package no.nav.syfo.sykmelding.pdl.model

import no.nav.syfo.util.toFormattedNameString

data class PdlPerson(
    val navn: Navn,
    val aktorId: String?,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

fun Navn.formatName(): String {
    return toFormattedNameString(fornavn, mellomnavn, etternavn)
}
