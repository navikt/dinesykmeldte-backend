package no.nav.syfo.minesykmeldte.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import java.sql.ResultSet
import java.time.ZoneOffset
import java.util.UUID

class MineSykmeldteDb(private val database: DatabaseInterface) {
    fun getMineSykmeldte(lederFnr: String): List<MinSykmeldtDbModel> {
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
                ps.executeQuery().toList { toMinSykmeldtDbModel() }
            }
        }
    }

    fun getSykmelding(sykmeldingId: UUID, lederFnr: String): Pair<SykmeldtDbModel, SykmeldingDbModel>? {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
                select s.sykmelding_id, s.pasient_fnr, s.orgnummer, s.orgnavn, s.sykmelding, s.lest, s.timestamp, s.latest_tom, sm.pasient_navn, sm.startdato_sykefravaer, sm.latest_tom
                  from sykmelding as s
                    iNnEr JoIn narmesteleder ON narmesteleder.pasient_fnr = s.pasient_fnr
                    iNnEr JoIn sykmeldt sm on narmesteleder.pasient_fnr = sm.pasient_fnr
                where s.sykmelding_id = ? AND narmesteleder.leder_fnr = ?
            """
            ).use { ps ->
                ps.setString(1, sykmeldingId.toString())
                ps.setString(2, lederFnr)
                ps.executeQuery().toSykmeldtSykmelding()
            }
        }
    }

}

private fun ResultSet.toMinSykmeldtDbModel(): MinSykmeldtDbModel = MinSykmeldtDbModel(
    narmestelederId = getString("narmeste_leder_id"),
    sykmeldtFnr = getString("pasient_fnr"),
    orgnummer = getString("orgnummer"),
    sykmeldtNavn = getString("pasient_navn"),
    startDatoSykefravar = getDate("startdato_sykefravaer").toLocalDate(),
    sykmeldingId = UUID.fromString(getString("sykmelding_id")),
    orgNavn = getString("orgnavn"),
    sykmelding = objectMapper.readValue(getString("sykmelding")),
    lestSykmelding = getBoolean("sykmelding_lest"),
    soknad = getString("soknad")?.let { objectMapper.readValue(it) },
    lestSoknad = getBoolean("soknad_lest")
)

private fun ResultSet.toSykmeldtSykmelding(): Pair<SykmeldtDbModel, SykmeldingDbModel>? =
    when (next()) {
        true -> SykmeldtDbModel(
            pasientFnr = getString("pasient_fnr"),
            pasientNavn = getString("pasient_navn"),
            startdatoSykefravaer = getDate("startdato_sykefravaer").toLocalDate(),
            latestTom = getDate("latest_tom").toLocalDate(),
        ) to SykmeldingDbModel(
            sykmeldingId = UUID.fromString(getString("sykmelding_id")),
            pasientFnr = getString("pasient_fnr"),
            orgnummer = getString("orgnummer"),
            orgnavn = getString("orgnavn"),
            sykmelding = objectMapper.readValue(getString("sykmelding")),
            lest = getBoolean("lest"),
            timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
            latestTom = getDate("latest_tom").toLocalDate(),
        )
        false -> null
    }


fun <T> ResultSet.toList(mapper: ResultSet.() -> T): List<T> = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}
