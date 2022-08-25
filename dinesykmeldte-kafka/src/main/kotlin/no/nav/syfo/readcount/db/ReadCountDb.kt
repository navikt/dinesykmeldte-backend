package no.nav.syfo.readcount.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.narmesteleder.db.toNarmestelederDbModel
import no.nav.syfo.objectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ReadCountDb(private val database: DatabaseInterface) {
    suspend fun getMineSykmeldte(lederFnr: String): List<MinSykmeldtDbModel> = withContext(Dispatchers.IO) {
        database.connection.use { connection ->
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
                       sk.lest as soknad_lest,
                       sm.sendt_til_arbeidsgiver_dato as sendt_til_arbeidsgiver_dato
                FROM narmesteleder AS nl
                    inner JOIN sykmeldt AS s ON s.pasient_fnr = nl.pasient_fnr
                    inner join sykmelding AS sm ON sm.pasient_fnr = nl.pasient_fnr AND sm.orgnummer = nl.orgnummer
                    left join soknad as sk on sk.sykmelding_id = sm.sykmelding_id AND sk.pasient_fnr = sm.pasient_fnr
                WHERE nl.leder_fnr = ?;
                """
            ).use { ps ->
                ps.setString(1, lederFnr)
                ps.executeQuery().toList { toMinSykmeldtDbModel() }
            }
        }
    }

    suspend fun getHendelser(lederFnr: String): List<HendelseDbModel> = withContext(Dispatchers.IO) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
           SELECT h.pasient_fnr,
              h.orgnummer,
              h.tekst, 
              h.id, 
              h.orgnummer,
              h.oppgavetype,
              h.lenke,
              h.timestamp,
              h.hendelse_id
           FROM hendelser h
                INNER JOIN narmesteleder n ON h.pasient_fnr = n.pasient_fnr and n.orgnummer = h.orgnummer
                INNER JOIN sykmeldt sm ON n.pasient_fnr = sm.pasient_fnr
           WHERE ferdigstilt = false and (utlopstidspunkt > ? or utlopstidspunkt is null)
           and n.leder_fnr = ?
        """
            ).use { ps ->
                ps.setTimestamp(1, Timestamp.from(Instant.now()))
                ps.setString(2, lederFnr)
                ps.executeQuery().toList { toHendelseDbModels() }
            }
        }
    }

    suspend fun getNarmesteleder(
        pasientFnr: String,
        orgnummer: String
    ): NarmestelederDbModel? = withContext(Dispatchers.IO) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
           SELECT * FROM narmesteleder WHERE pasient_fnr = ? AND orgnummer = ?;
        """
            ).use {
                it.setString(1, pasientFnr)
                it.setString(2, orgnummer)
                it.executeQuery().toList { toNarmestelederDbModel() }.firstOrNull()
            }
        }
    }
}

private fun ResultSet.toHendelseDbModels() =
    HendelseDbModel(
        id = getString("id"),
        hendelseId = UUID.fromString(getString("hendelse_id")),
        pasientFnr = getString("pasient_fnr"),
        orgnummer = getString("orgnummer"),
        oppgavetype = getString("oppgavetype"),
        lenke = getString("lenke"),
        tekst = getString("tekst"),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
        utlopstidspunkt = null,
        ferdigstilt = false,
        ferdigstiltTimestamp = null
    )

private fun ResultSet.toMinSykmeldtDbModel(): MinSykmeldtDbModel = MinSykmeldtDbModel(
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
    lestSoknad = getBoolean("soknad_lest"),
    sendtTilArbeidsgiverDato = getTimestamp("sendt_til_arbeidsgiver_dato")?.toInstant()?.atOffset(ZoneOffset.UTC),
)

fun <T> ResultSet.toList(mapper: ResultSet.() -> T): List<T> = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}
