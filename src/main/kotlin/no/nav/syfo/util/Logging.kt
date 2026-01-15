package no.nav.syfo.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.MarkerFactory

fun <T : Any> T.logger(): Logger {
    return LoggerFactory.getLogger(this.javaClass)
}

fun logger(name: String): Logger {
    return LoggerFactory.getLogger(name)
}

// Log entries marked with this team logs marker are automatically sent to dedicated team logs.
val teamLogsMarker: Marker? = MarkerFactory.getMarker("TEAM_LOGS")
