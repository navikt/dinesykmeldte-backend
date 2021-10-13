package no.nav.syfo.application.database

import java.sql.Connection

interface DatabaseInterface {
    val connection: Connection
}
