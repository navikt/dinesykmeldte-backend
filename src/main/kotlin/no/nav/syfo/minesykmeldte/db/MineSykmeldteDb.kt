package no.nav.syfo.minesykmeldte.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.objectMapper
import java.sql.ResultSet

class MineSykmeldteDb(private val database: DatabaseInterface) {
    fun getMineSykmeldte(lederFnr: String): List<SykmeldtDbModel> {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT nl.narmeste_leder_id,
                       nl.pasient_fnr,
                       nl.orgnummer,
                       s.pasient_navn,
                       s.startdato_sykefravaer,
                       sm.sykmelding_id,
                       sm.orgnavn,
                       sm.sykmelding,
                       sm.lest as sykmelding_lest,
                       sk.soknad,
                       sk.sendt_dato,
                       sk.lest as soknad_lest
                FROM narmesteleder AS nl
                    inner JOIN sykmeldt AS s ON s.pasient_fnr = nl.pasient_fnr
                    inner join sykmelding AS sm ON sm.pasient_fnr = nl.pasient_fnr AND sm.orgnummer = nl.orgnummer
                    left join soknad as sk on sk.sykmelding_id = sm.sykmelding_id
                WHERE nl.leder_fnr = ?;
                """
            ).use { ps ->
                ps.setString(1, lederFnr)
                ps.executeQuery().toList { toSykmeldtDbModel() }
            }
        }
    }

    fun ResultSet.toSykmeldtDbModel(): SykmeldtDbModel {
        return SykmeldtDbModel(
            narmestelederId = getString("narmeste_leder_id"),
            sykmeldtFnr = getString("pasient_fnr"),
            orgnummer = getString("orgnummer"),
            sykmeldtNavn = getString("pasient_navn"),
            startDatoSykefravar = getDate("startdato_sykefravaer").toLocalDate(),
            sykmeldingId = getString("sykmelding_id"),
            orgNavn = getString("orgnavn"),
            sykmelding = objectMapper.readValue(getString("sykmelding")),
            lestSykmelding = getBoolean("sykmelding_lest"),
            soknad = getString("soknad")?.let { objectMapper.readValue(it) },
            lestSoknad = getBoolean("soknad_lest")
        )
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T): List<T> = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}
