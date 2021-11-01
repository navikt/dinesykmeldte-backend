package no.nav.syfo.minesykmeldte.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.minesykmeldte.model.Sykmeldt
import java.sql.ResultSet

class MineSykmeldteDb(private val database: DatabaseInterface) {
    fun getMineSykmeldte(lederFnr: String): List<Sykmeldt> {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT nl.narmeste_leder_id,
                       nl.pasient_fnr,
                       nl.orgnummer,
                       s.pasient_navn,
                       s.startdato_sykefravaer,
                       sm.orgnavn,
                       sm.sykmelding,
                       sm.lest,
                       sk.sykmelding_id,
                       sk.soknad,
                       sk.sendt_dato,
                       sk.lest
                FROM narmesteleder AS nl
                         INNER JOIN sykmelding AS sm ON sm.pasient_fnr = nl.pasient_fnr AND sm.orgnummer = nl.orgnummer
                         INNER JOIN soknad AS sk ON sk.pasient_fnr = nl.pasient_fnr AND sk.orgnummer = nl.orgnummer
                         INNER JOIN sykmeldt AS s ON s.pasient_fnr = nl.pasient_fnr
                WHERE nl.leder_fnr = ?;
                """
            ).use { ps ->
                ps.setString(1, lederFnr)
                ps.executeQuery().toList { toSykmeldt() }
            }
        }
    }

    fun ResultSet.toSykmeldt(): Sykmeldt {
        return Sykmeldt()
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T): List<T> = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}
