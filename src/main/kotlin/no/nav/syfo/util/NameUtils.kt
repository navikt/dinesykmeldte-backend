package no.nav.syfo.util

fun toFormattedNameString(fornavn: String, mellomnavn: String?, etternavn: String): String {
    return if (mellomnavn.isNullOrEmpty()) {
        capitalizeFirstLetter("$fornavn $etternavn")
    } else {
        capitalizeFirstLetter("$fornavn $mellomnavn $etternavn")
    }
}

private fun capitalizeFirstLetter(string: String): String {
    return string
        .lowercase()
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { char -> char.titlecaseChar() } }
        .split("-")
        .joinToString("-") { it.replaceFirstChar { char -> char.titlecaseChar() } }
        .trimEnd()
}
