package no.nav.syfo.hendelser.db

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.log
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime

class HendelserDb(private val database: DatabaseInterface) {

    fun insertHendelse(hendelseDbModel: HendelseDbModel) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                    INSERT INTO hendelser(id, pasient_fnr, orgnummer, oppgavetype, lenke, tekst, timestamp, 
                                          utlopstidspunkt, ferdigstilt, ferdigstilt_timestamp)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id, oppgavetype) DO NOTHING;
            """,
            ).use { preparedStatement ->
                preparedStatement.setString(1, hendelseDbModel.id)
                preparedStatement.setString(2, hendelseDbModel.pasientFnr)
                preparedStatement.setString(3, hendelseDbModel.orgnummer)
                preparedStatement.setString(4, hendelseDbModel.oppgavetype)
                preparedStatement.setString(5, hendelseDbModel.lenke)
                preparedStatement.setString(6, hendelseDbModel.tekst)
                preparedStatement.setTimestamp(7, Timestamp.from(hendelseDbModel.timestamp.toInstant()))
                preparedStatement.setTimestamp(8, hendelseDbModel.utlopstidspunkt?.let { Timestamp.from(it.toInstant()) })
                preparedStatement.setBoolean(9, hendelseDbModel.ferdigstilt)
                preparedStatement.setTimestamp(10, hendelseDbModel.ferdigstiltTimestamp?.let { Timestamp.from(it.toInstant()) })
                preparedStatement.executeUpdate()
            }
            connection.updateSistOppdatertForSykmeldt(hendelseDbModel.pasientFnr)
            connection.commit()
        }
    }

    private fun Connection.updateSistOppdatertForSykmeldt(fnr: String) {
        this.prepareStatement(
            """
                UPDATE sykmeldt SET sist_oppdatert = ? WHERE pasient_fnr = ?;
                """,
        ).use {
            it.setObject(1, LocalDate.now())
            it.setString(2, fnr)
            it.executeUpdate()
        }
    }

    fun ferdigstillHendelse(id: String, ferdigstiltTimestamp: OffsetDateTime) {
        database.connection.use { connection ->
            connection.ferdigstillHendelse(id, ferdigstiltTimestamp)
            connection.commit()
            log.info("Ferdigstilt hendelse med id $id")
        }
    }

    private fun Connection.ferdigstillHendelse(id: String, ferdigstiltTimestamp: OffsetDateTime) {
        this.prepareStatement(
            """
                UPDATE hendelser SET ferdigstilt=?, ferdigstilt_timestamp=? WHERE id=? AND ferdigstilt != true;
                """,
        ).use {
            it.setBoolean(1, true)
            it.setTimestamp(2, Timestamp.from(ferdigstiltTimestamp.toInstant()))
            it.setString(3, id)
            it.executeUpdate()
        }
    }
}
