package no.nav.syfo.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

val securelog: Logger = LoggerFactory.getLogger("securelog")

fun <T : Any> T.logger(): Logger {
    return LoggerFactory.getLogger(this.javaClass)
}

fun logger(name: String): Logger {
    return LoggerFactory.getLogger(name)
}
