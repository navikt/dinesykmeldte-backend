package no.nav.syfo.soknad.db

import no.nav.syfo.application.database.DatabaseInterface
import java.sql.Timestamp

class SoknadDb(private val database: DatabaseInterface) {

    fun insert(soknadDbModel: SoknadDbModel) {
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
                        latest_tom) 
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?) on CONFLICT DO NOTHING;
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
                preparedStatement.setObject(9, soknadDbModel.latestTom)
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }
}
