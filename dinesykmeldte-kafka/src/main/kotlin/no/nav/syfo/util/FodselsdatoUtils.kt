package no.nav.syfo.util

import java.time.LocalDate

fun extractBornDate(personIdent: String): LocalDate =
    LocalDate.of(extractBornYear(personIdent), extractBornMonth(personIdent), extractBornDay(personIdent))

fun extractBornYear(personIdent: String): Int {
    val lastTwoDigitsOfYear = extractLastTwoDigistOfyear(personIdent)
    val individualDigits = extractIndividualDigits(personIdent)
    if (lastTwoDigitsOfYear in (0..99) && individualDigits in (0..499)) {
        return 1900 + lastTwoDigitsOfYear
    }

    if (lastTwoDigitsOfYear in (54..99) && individualDigits in 500..749) {
        return 1800 + lastTwoDigitsOfYear
    }

    if (lastTwoDigitsOfYear in (0..39) && individualDigits in 500..999) {
        return 2000 + lastTwoDigitsOfYear
    }
    return 1900 + lastTwoDigitsOfYear
}

fun extractBornDay(personIdent: String): Int {
    val day = personIdent.substring(0..1).toInt()
    return if (day < 40) day else day - 40
}

fun extractBornMonth(personIdent: String): Int = personIdent.substring(2..3).toInt()

fun extractIndividualDigits(personIdent: String): Int = personIdent.substring(6, 9).toInt()

fun extractLastTwoDigistOfyear(personIdent: String): Int = personIdent.substring(4, 6).toInt()
