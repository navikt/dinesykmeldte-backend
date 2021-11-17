package no.nav.syfo.hendelser.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.hendelser.OPPGAVETYPE_LES_SOKNAD
import no.nav.syfo.hendelser.OPPGAVETYPE_LES_SYKMELDING
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

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

    fun ferdigstillHendelse(id: String, maybeOppgavetype: String?, ferdigstiltTimestamp: OffsetDateTime) {
        database.connection.use { connection ->
            val oppgavetype = maybeOppgavetype
                ?: (
                    connection.finnHendelse(id)?.oppgavetype
                        ?: throw IllegalStateException("Fant ingen hendelse for id $id")
                    )

            if (connection.hendelseFinnesOgErIkkeFerdigstilt(id, oppgavetype)) {
                connection.ferdigstillHendelse(id, oppgavetype, ferdigstiltTimestamp)
                if (oppgavetype == OPPGAVETYPE_LES_SYKMELDING) {
                    connection.settSykmeldingLest(id)
                } else if (oppgavetype == OPPGAVETYPE_LES_SOKNAD) {
                    connection.settSoknadLest(id)
                }
                connection.commit()
                log.info("Ferdigstilt hendelse med id $id og type $oppgavetype")
            } else {
                log.info("Fant ingen åpen hendelse med id $id for oppgavetype $oppgavetype")
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

    private fun Connection.finnHendelse(id: String): HendelseDbModel? {
        return this.prepareStatement(
            """
                SELECT * FROM hendelser WHERE id=?;
                """
        ).use {
            it.setString(1, id)
            it.executeQuery().toList { toHendelseDbModel() }.firstOrNull()
        }
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

    fun getSykmelding(sykmeldingId: String): SykmeldingDbModel? {
        return database.connection.use {
            it.prepareStatement(
                """
                    SELECT * FROM sykmelding WHERE sykmelding_id = ?;
                """
            ).use { ps ->
                ps.setString(1, sykmeldingId)
                ps.executeQuery().toList { toSykmeldingDbModel() }.firstOrNull()
            }
        }
    }

    private fun ResultSet.toSykmeldingDbModel(): SykmeldingDbModel =
        SykmeldingDbModel(
            sykmeldingId = getString("sykmelding_id"),
            pasientFnr = getString("pasient_fnr"),
            orgnummer = getString("orgnummer"),
            orgnavn = getString("orgnavn"),
            sykmelding = objectMapper.readValue(getString("sykmelding"), ArbeidsgiverSykmelding::class.java),
            lest = getBoolean("lest"),
            timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
            latestTom = getObject("latest_tom", LocalDate::class.java)
        )

    fun getSoknad(soknadId: String): SoknadDbModel? {
        return database.connection.use {
            it.prepareStatement(
                """
                    SELECT * FROM soknad WHERE soknad_id = ?;
                """
            ).use { ps ->
                ps.setString(1, soknadId)
                ps.executeQuery().toList { toSoknadDbModel() }.firstOrNull()
            }
        }
    }

    private fun ResultSet.toSoknadDbModel(): SoknadDbModel =
        SoknadDbModel(
            soknadId = getString("soknad_id"),
            sykmeldingId = getString("sykmelding_id"),
            pasientFnr = getString("pasient_fnr"),
            orgnummer = getString("orgnummer"),
            soknad = objectMapper.readValue(getString("soknad"), SykepengesoknadDTO::class.java),
            sendtDato = getObject("sendt_dato", LocalDate::class.java),
            lest = getBoolean("lest"),
            timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
            tom = getObject("tom", LocalDate::class.java)
        )

    private fun ResultSet.toHendelseDbModel(): HendelseDbModel =
        HendelseDbModel(
            id = getString("id"),
            pasientFnr = getString("pasient_fnr"),
            orgnummer = getString("orgnummer"),
            oppgavetype = getString("oppgavetype"),
            lenke = getString("lenke"),
            tekst = getString("tekst"),
            timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
            utlopstidspunkt = getTimestamp("utlopstidspunkt")?.toInstant()?.atOffset(ZoneOffset.UTC),
            ferdigstilt = getBoolean("ferdigstilt"),
            ferdigstiltTimestamp = getTimestamp("ferdigstilt_timestamp")?.toInstant()?.atOffset(ZoneOffset.UTC)
        )
}
