package no.nav.syfo.soknad.db

import no.nav.syfo.application.database.DatabaseInterface
import java.sql.Timestamp

class SoknadDb(private val database: DatabaseInterface) {

    fun insertOrUpdate(soknadDbModel: SoknadDbModel) {
        database.connection.use { connection ->
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
            """
            ).use { preparedStatement ->
                preparedStatement.setString(1, soknadDbModel.soknadId)
                preparedStatement.setString(2, soknadDbModel.sykmeldingId)
                preparedStatement.setString(3, soknadDbModel.pasientFnr)
                preparedStatement.setString(4, soknadDbModel.orgnummer)
                preparedStatement.setObject(5, soknadDbModel.soknad.toPGObject())
                preparedStatement.setObject(6, soknadDbModel.sendtDato)
                preparedStatement.setBoolean(7, soknadDbModel.lest)
                preparedStatement.setTimestamp(8, Timestamp.from(soknadDbModel.timestamp.toInstant()))
                preparedStatement.setObject(9, soknadDbModel.tom)
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }

    fun deleteSoknad(id: String) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                delete from soknad where soknad_id = ?;
            """
            ).use { ps ->
                ps.setString(1, id)
                ps.executeUpdate()
            }
            connection.commit()
        }
    }
}
