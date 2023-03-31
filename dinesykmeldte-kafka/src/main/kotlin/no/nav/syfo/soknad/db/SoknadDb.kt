package no.nav.syfo.soknad.db

import no.nav.syfo.database.DatabaseInterface
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDate

class SoknadDb(private val database: DatabaseInterface) {

    fun insertOrUpdate(soknadDbModel: SoknadDbModel) {
        database.connection.use { connection ->
            val fnr = connection.getFnr(soknadDbModel.sykmeldingId.toString()) ?: soknadDbModel.pasientFnr
            connection.prepareStatement(
                """
               insert into soknad(
                        soknad_id, 
                        sykmelding_id, 
                        pasient_fnr, 
                        orgnummer, 
                        soknad,
                        sendt_dato, 
                        lest, 
                        timestamp, 
                        tom) 
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?) on CONFLICT(soknad_id) do update 
                        set soknad = excluded.soknad,
                        sykmelding_id = excluded.sykmelding_id,
                        pasient_fnr = excluded.pasient_fnr,
                        orgnummer = excluded.orgnummer,
                        timestamp = excluded.timestamp,
                        sendt_dato = excluded.sendt_dato,
                        lest = excluded.lest,
                        tom = excluded.tom
                    ;
            """,
            ).use { preparedStatement ->
                preparedStatement.setString(1, soknadDbModel.soknadId)
                preparedStatement.setString(2, soknadDbModel.sykmeldingId)
                preparedStatement.setString(3, fnr)
                preparedStatement.setString(4, soknadDbModel.orgnummer)
                preparedStatement.setObject(5, soknadDbModel.soknad.toPGObject())
                preparedStatement.setObject(6, soknadDbModel.sendtDato)
                preparedStatement.setBoolean(7, soknadDbModel.lest)
                preparedStatement.setTimestamp(8, Timestamp.from(soknadDbModel.timestamp.toInstant()))
                preparedStatement.setObject(9, soknadDbModel.tom)
                preparedStatement.executeUpdate()
            }
            connection.updateSistOppdatertForSykmeldt(fnr)
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

    fun deleteSoknad(id: String) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                delete from soknad where soknad_id = ?;
            """,
            ).use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
            connection.commit()
        }
    }

    private fun Connection.getFnr(sykmeldingId: String): String? {
        return this.prepareStatement(
            """
            select pasient_fnr from sykmelding where sykmelding_id = ?;
            """,
        ).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldingId)
            preparedStatement.executeQuery().use {
                when (it.next()) {
                    true -> it.getString("pasient_fnr")
                    else -> null
                }
            }
        }
    }
}
