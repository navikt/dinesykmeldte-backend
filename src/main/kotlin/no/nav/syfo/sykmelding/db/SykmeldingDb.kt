package no.nav.syfo.sykmelding.db

import no.nav.syfo.application.database.DatabaseInterface
import java.sql.Connection
import java.sql.Timestamp

class SykmeldingDb(private val database: DatabaseInterface) {



    fun insertOrUpdate(sykmeldingDbModel: SykmeldingDbModel, sykmeldt: SykmeldtDbModel) {
        database.connection.use { connection ->
            connection.insertOrUpdateSykmeldt(sykmeldt)
            connection.prepareStatement(
                """
               insert into sykmelding(
                        sykmelding_id, 
                        pasient_fnr, 
                        orgnummer, 
                        orgnavn, 
                        sykmelding, 
                        lest, 
                        timestamp, 
                        latest_tom) 
                    values (?, ?, ?, ?, ?, ?, ?, ?) 
               on conflict (sykmelding_id) do update 
                    set pasient_fnr = ?,
                        orgnummer = ?,
                        orgnavn = ?,
                        sykmelding = ?,
                        lest = ?,
                        timestamp = ?,
                        latest_tom = ?;
            """
            ).use { preparedStatement ->
                preparedStatement.setString(1, sykmeldingDbModel.sykmeldingId)
                // insert
                preparedStatement.setString(2, sykmeldingDbModel.pasientFnr)
                preparedStatement.setString(3, sykmeldingDbModel.orgnummer)
                preparedStatement.setString(4, sykmeldingDbModel.orgnavn)
                preparedStatement.setObject(5, sykmeldingDbModel.sykmelding.toPGObject())
                preparedStatement.setBoolean(6, sykmeldingDbModel.lest)
                preparedStatement.setTimestamp(7, Timestamp.from(sykmeldingDbModel.timestamp.toInstant()))
                preparedStatement.setObject(8, sykmeldingDbModel.latestTom)
                // update
                preparedStatement.setString(9, sykmeldingDbModel.pasientFnr)
                preparedStatement.setString(10, sykmeldingDbModel.orgnummer)
                preparedStatement.setString(11, sykmeldingDbModel.orgnavn)
                preparedStatement.setObject(12, sykmeldingDbModel.sykmelding.toPGObject())
                preparedStatement.setBoolean(13, sykmeldingDbModel.lest)
                preparedStatement.setTimestamp(14, Timestamp.from(sykmeldingDbModel.timestamp.toInstant()))
                preparedStatement.setObject(15, sykmeldingDbModel.latestTom)
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

    private fun Connection.insertOrUpdateSykmeldt(sykmeldt: SykmeldtDbModel) {
        this.prepareStatement(
            """
               insert into sykmeldt(pasient_fnr, pasient_navn, startdato_sykefravaer, latest_tom) 
                    values (?, ?, ?, ?) 
               on conflict (pasient_fnr) do update
                set pasient_navn = ?,
                    startdato_sykefravaer = ?,
                    latest_tom = ?;
            """
        ).use { preparedStatement ->
            preparedStatement.setString(1, sykmeldt.pasientFnr)
            // insert
            preparedStatement.setString(2, sykmeldt.pasientNavn)
            preparedStatement.setObject(3, sykmeldt.startdatoSykefravaer)
            preparedStatement.setObject(4, sykmeldt.latestTom)
            // update
            preparedStatement.setString(5, sykmeldt.pasientNavn)
            preparedStatement.setObject(6, sykmeldt.startdatoSykefravaer)
            preparedStatement.setObject(7, sykmeldt.latestTom)
            preparedStatement.executeUpdate()
        }
    }
}
