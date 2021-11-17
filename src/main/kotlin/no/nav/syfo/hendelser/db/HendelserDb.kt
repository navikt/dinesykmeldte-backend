package no.nav.syfo.hendelser.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.hendelser.OPPGAVETYPE_LES_SOKNAD
import no.nav.syfo.hendelser.OPPGAVETYPE_LES_SYKMELDING
import no.nav.syfo.log
import java.sql.Connection
import java.sql.Timestamp
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
            """
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
            connection.commit()
        }
    }

    fun ferdigstillHendelse(id: String, oppgavetype: String?, ferdigstiltTimestamp: OffsetDateTime) {
        database.connection.use { connection ->
            if (oppgavetype == null || oppgavetype == OPPGAVETYPE_LES_SYKMELDING || oppgavetype == OPPGAVETYPE_LES_SOKNAD) {
                connection.settSykmeldingLest(id)
                connection.settSoknadLest(id)
                connection.commit()
            } else {
                if (connection.hendelseFinnesOgErIkkeFerdigstilt(id, oppgavetype)) {
                    connection.ferdigstillHendelse(id, oppgavetype, ferdigstiltTimestamp)
                    connection.commit()
                    log.info("Ferdigstilt hendelse med id $id og type $oppgavetype")
                } else {
                    log.info("Fant ingen åpen hendelse med id $id for oppgavetype $oppgavetype")
                }
            }
        }
    }

    private fun Connection.hendelseFinnesOgErIkkeFerdigstilt(id: String, oppgavetype: String): Boolean =
        this.prepareStatement(
            """
                SELECT 1 FROM hendelser WHERE id=? AND oppgavetype=? AND ferdigstilt != true;
                """
        ).use {
            it.setString(1, id)
            it.setString(2, oppgavetype)
            it.executeQuery().next()
        }

    private fun Connection.ferdigstillHendelse(id: String, oppgavetype: String, ferdigstiltTimestamp: OffsetDateTime) {
        this.prepareStatement(
            """
                UPDATE hendelser SET ferdigstilt=?, ferdigstilt_timestamp=? WHERE id=? AND oppgavetype=?;
                """
        ).use {
            it.setBoolean(1, true)
            it.setTimestamp(2, Timestamp.from(ferdigstiltTimestamp.toInstant()))
            it.setString(3, id)
            it.setString(4, oppgavetype)
            it.executeUpdate()
        }
    }

    private fun Connection.settSykmeldingLest(id: String) {
        this.prepareStatement(
            """
                UPDATE sykmelding SET lest=? WHERE sykmelding_id=?;
                """
        ).use {
            it.setBoolean(1, true)
            it.setString(2, id)
            it.executeUpdate()
        }
    }

    private fun Connection.settSoknadLest(id: String) {
        this.prepareStatement(
            """
                UPDATE soknad SET lest=? WHERE soknad_id=?;
                """
        ).use {
            it.setBoolean(1, true)
            it.setString(2, id)
            it.executeUpdate()
        }
    }
}
