package no.nav.syfo.sykmelding.db

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import no.nav.syfo.application.database.DatabaseInterface
import java.time.LocalDateTime

class SykmeldingDb(private val database: DatabaseInterface) {
    fun insertOrUpdateSykmelding(sykmelding: SykmeldingDbModel) {
        database.connection.use { connection ->
            connection.insertOrUpdateSykmelding(sykmelding)
            connection.commit()
        }
    }

    fun insertOrUpdateSykmeldt(sykmeldt: SykmeldtDbModel) {
        database.connection.use { connection ->
            connection.insertOrUpdateSykmeldt(sykmeldt)
            connection.commit()
        }
    }

    private fun Connection.insertOrUpdateSykmelding(
        sykmeldingDbModel: SykmeldingDbModel,
    ) {
        this.prepareStatement(
                """insert into sykmelding(
                            sykmelding_id, 
                            pasient_fnr, 
                            orgnummer, 
                            orgnavn, 
                            sykmelding, 
                            lest, 
                            timestamp, 
                            latest_tom,
                            sendt_til_arbeidsgiver_dato,
                            egenmeldingsdager) 
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) 
                   on conflict (sykmelding_id) do update 
                        set pasient_fnr = excluded.pasient_fnr,
                            orgnummer = excluded.orgnummer,
                            orgnavn = excluded.orgnavn,
                            sykmelding = excluded.sykmelding,
                            lest = excluded.lest,
                            timestamp = excluded.timestamp,
                            latest_tom = excluded.latest_tom,
                            sendt_til_arbeidsgiver_dato = excluded.sendt_til_arbeidsgiver_dato,
                            egenmeldingsdager = excluded.egenmeldingsdager;""",
            )
            .use { preparedStatement ->
                val sendtTilArbeidsgiverDato =
                    if (sykmeldingDbModel.sendtTilArbeidsgiverDato != null)
                        Timestamp.from(sykmeldingDbModel.sendtTilArbeidsgiverDato.toInstant())
                    else null
                preparedStatement.setString(1, sykmeldingDbModel.sykmeldingId)
                // insert
                preparedStatement.setString(2, sykmeldingDbModel.pasientFnr)
                preparedStatement.setString(3, sykmeldingDbModel.orgnummer)
                preparedStatement.setString(4, sykmeldingDbModel.orgnavn)
                preparedStatement.setObject(5, sykmeldingDbModel.sykmelding.toPGObject())
                preparedStatement.setBoolean(6, sykmeldingDbModel.lest)
                preparedStatement.setTimestamp(
                    7,
                    Timestamp.from(sykmeldingDbModel.timestamp.toInstant())
                )
                preparedStatement.setObject(8, sykmeldingDbModel.latestTom)
                preparedStatement.setTimestamp(9, sendtTilArbeidsgiverDato)
                preparedStatement.setObject(10, sykmeldingDbModel.egenmeldingsdager?.toPGObject())
                preparedStatement.executeUpdate()
            }
        this.updateSoknadFnr(
            sykmeldingId = sykmeldingDbModel.sykmeldingId,
            nyttFnr = sykmeldingDbModel.pasientFnr,
        )
    }

    fun remove(sykmeldingId: String) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
               delete from sykmelding where sykmelding_id = ?;
            """,
                )
                .use { ps ->
                    ps.setString(1, sykmeldingId)
                    ps.executeUpdate()
                }
            connection.commit()
        }
    }

    private fun Connection.insertOrUpdateSykmeldt(sykmeldt: SykmeldtDbModel) {
        this.prepareStatement(
                """
               insert into sykmeldt(pasient_fnr, pasient_navn, startdato_sykefravaer, latest_tom, sist_oppdatert) 
                    values (?, ?, ?, ?, ?) 
               on conflict (pasient_fnr) do update
                set pasient_navn = ?,
                    startdato_sykefravaer = ?,
                    latest_tom = ?,
                    sist_oppdatert = ?;
            """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, sykmeldt.pasientFnr)
                // insert
                preparedStatement.setString(2, sykmeldt.pasientNavn)
                preparedStatement.setObject(3, sykmeldt.startdatoSykefravaer)
                preparedStatement.setObject(4, sykmeldt.latestTom)
                preparedStatement.setObject(5, sykmeldt.sistOppdatert)
                // update
                preparedStatement.setString(6, sykmeldt.pasientNavn)
                preparedStatement.setObject(7, sykmeldt.startdatoSykefravaer)
                preparedStatement.setObject(8, sykmeldt.latestTom)
                preparedStatement.setObject(9, sykmeldt.sistOppdatert)
                preparedStatement.executeUpdate()
            }
    }

    private fun Connection.updateSoknadFnr(sykmeldingId: String, nyttFnr: String) {
        this.prepareStatement(
                """
                UPDATE soknad SET pasient_fnr =? WHERE sykmelding_id=?;
                """,
            )
            .use {
                it.setString(1, nyttFnr)
                it.setString(2, sykmeldingId)
                it.executeUpdate()
            }
    }

    fun getSykmeldingInfo(sykmeldingId: String): no.nav.syfo.sykmelding.db.SykmeldingInfo? {
        return database.connection.use { connection ->
            connection
                .prepareStatement(
                    """select sykmelding_id, pasient_fnr, latest_tom from sykmelding where sykmelding_id = ?"""
                )
                .use { ps ->
                    ps.setString(1, sykmeldingId)
                    ps.executeQuery().let {
                        when (it.next()) {
                            true -> it.toSykmeldingInfo()
                            else -> null
                        }
                    }
                }
        }
    }

    fun getSykmeldingInfos(fnr: String): List<no.nav.syfo.sykmelding.db.SykmeldingInfo> {
        return database.connection.use { connection ->
            connection
                .prepareStatement(
                    """select sykmelding_id, latest_tom, pasient_fnr from sykmelding where pasient_fnr = ?"""
                )
                .use { ps ->
                    ps.setString(1, fnr)
                    ps.executeQuery().toList { toSykmeldingInfo() }
                }
        }
    }

    fun getActiveSendtSykmeldingsperioder(
        employeeIdentificationNumber: String,
        orgnummer: String,
    ): List<Int>? {
        val selectStatement = """
        SELECT count(*) as countSykmeldinger
FROM sykmelding s,
     jsonb_array_elements(s.sykmelding -> 'sykmeldingsperioder') AS period
WHERE pasient_fnr = ?
  AND orgnummer = ?
  AND (latest_tom >= now()::DATE - INTERVAL '16 days' OR
       EXISTS (SELECT 1
               FROM jsonb_array_elements(sykmelding -> 'sykmeldingsperioder') AS period
               WHERE now() BETWEEN (period ->> 'fom')::DATE  + INTERVAL '16 days' 
               AND ((period ->> 'tom')::DATE + INTERVAL '16 days'))
    );
    """.trimIndent()

        return database.connection.use { connection ->
            connection.prepareStatement(selectStatement).use {
                it.setString(1, employeeIdentificationNumber)
                it.setString(2, orgnummer)
                it.executeQuery().toList { getInt("countSykmeldinger") }
            }
        }
    }

    fun deleteSykmeldt(fnr: String) {
        database.connection.use { connection ->
            connection.prepareStatement("""delete from sykmeldt where pasient_fnr = ?""").use { ps
                ->
                ps.setString(1, fnr)
                ps.executeUpdate()
            }
            connection.commit()
        }
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) =
    mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }

private fun ResultSet.toSykmeldingInfo(): SykmeldingInfo {
    return SykmeldingInfo(
        sykmeldingId = getString("sykmelding_id"),
        latestTom = getDate("latest_tom").toLocalDate(),
        fnr = getString("pasient_fnr"),
    )
}
