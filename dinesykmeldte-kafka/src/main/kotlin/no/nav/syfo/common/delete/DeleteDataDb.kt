package no.nav.syfo.common.delete

import no.nav.syfo.database.DatabaseInterface
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

class DeleteDataDb(private val database: DatabaseInterface) {
    fun deleteOldData(date: LocalDate): DeleteResult {
        return database.connection.use { connection ->
            val result =
                DeleteResult(
                    deletedSykmeldt = deleteSykmeldt(connection, date),
                    deletedSykmelding = deleteSykmelding(connection, date),
                    deletedSoknader = deleteSoknader(connection, date),
                    deletedHendelser = deleteHendelser(connection),
                )
            connection.commit()
            result
        }
    }

    private fun deleteSoknader(connection: Connection, date: LocalDate): Int {
        return connection.prepareStatement(
            """
            delete from soknad where tom < ?;
        """,
        ).use { ps ->
            ps.setDate(1, Date.valueOf(date))
            ps.executeUpdate()
        }
    }

    private fun deleteSykmelding(connection: Connection, date: LocalDate): Int {
        return connection.prepareStatement(
            """
            delete from sykmelding where latest_tom < ?;
        """,
        ).use { ps ->
            ps.setDate(1, Date.valueOf(date))
            ps.executeUpdate()
        }
    }

    private fun deleteSykmeldt(connection: Connection, date: LocalDate): Int {
        return connection.prepareStatement(
            """
            delete from sykmeldt where latest_tom < ?;
        """,
        ).use { ps ->
            ps.setDate(1, Date.valueOf(date))
            ps.executeUpdate()
        }
    }
    private fun deleteHendelser(connection: Connection): Int {
        return connection.prepareStatement(
            """
            delete from hendelser h where utlopstidspunkt < ? OR NOT EXISTS(select 1 from sykmeldt s where s.pasient_fnr = h.pasient_fnr);
        """,
        ).use { ps ->
            ps.setTimestamp(1, Timestamp.from(Instant.now()))
            ps.executeUpdate()
        }
    }
}
