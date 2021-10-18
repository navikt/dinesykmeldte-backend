package no.nav.syfo.sykmelding.db

import no.nav.syfo.application.database.DatabaseInterface
import java.sql.Timestamp

class SykmeldingDb(private val database: DatabaseInterface) {

    fun insertOrUpdate(sykmeldingDbModel: SykmeldingDbModel) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               insert into sykmelding(
                    sykmelding_id, 
                    pasient_fnr, 
                    pasient_navn, 
                    orgnummer, 
                    orgnavn, 
                    startdato_sykefravaer, 
                    sykmelding, 
                    lest, 
                    timestamp, 
                    latest_tom) 
               values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (sykmelding_id) do nothing ;
            """
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingDbModel.sykmeldingId)
                preparedStatement.setString(2, sykmeldingDbModel.pasientFnr)
                preparedStatement.setString(3, sykmeldingDbModel.pasientNavn)
                preparedStatement.setString(4, sykmeldingDbModel.orgnummer)
                preparedStatement.setString(5, sykmeldingDbModel.orgnavn)
                preparedStatement.setObject(6, sykmeldingDbModel.startdatoSykefravaer)
                preparedStatement.setObject(7, sykmeldingDbModel.sykmelding.toPGObject())
                preparedStatement.setBoolean(8, sykmeldingDbModel.lest)
                preparedStatement.setTimestamp(9, Timestamp.from(sykmeldingDbModel.timestamp.toInstant()))
                preparedStatement.setObject(10, sykmeldingDbModel.latestTom)
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }

    fun remove(sykmeldingId: String) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               delete from sykmelding where sykmelding_id = ?;
            """
            ).use { ps ->
                ps.setString(1, sykmeldingId)
                ps.executeUpdate()
            }
            connection.commit()
        }
    }
}
