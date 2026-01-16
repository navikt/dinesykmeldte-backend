package no.nav.syfo.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun <T : Any> T.logger(): Logger {
    return LoggerFactory.getLogger(this.javaClass)
}

fun logger(name: String): Logger {
    return LoggerFactory.getLogger(name)
}

// Dedicated logger for team logs. Use this if you need secure logs (isolated from standard application logs).
val teamLogsLogger: Logger = LoggerFactory.getLogger("team-logs-logger")
