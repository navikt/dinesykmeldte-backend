package no.nav.syfo.sykmelding.pdl.model

data class PdlPerson(
    val navn: Navn,
    val aktorId: String?
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

fun Navn.toFormattedNameString(): String {
    return if (mellomnavn.isNullOrEmpty()) {
        capitalizeFirstLetter("$fornavn $etternavn")
    } else {
        capitalizeFirstLetter("$fornavn $mellomnavn $etternavn")
    }
}

private fun capitalizeFirstLetter(string: String): String {
    return string.toLowerCase()
        .split(" ").joinToString(" ") { it.capitalize() }
        .split("-").joinToString("-") { it.capitalize() }.trimEnd()
}
