package no.nav.syfo.database

import java.sql.Connection

interface DatabaseInterface {
    val connection: Connection
}
