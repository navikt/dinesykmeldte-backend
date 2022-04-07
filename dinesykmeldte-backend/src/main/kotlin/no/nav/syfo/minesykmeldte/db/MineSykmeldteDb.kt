package no.nav.syfo.minesykmeldte.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
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
                    left join soknad as sk on sk.sykmelding_id = sm.sykmelding_id AND sk.pasient_fnr = sm.pasient_fnr
                WHERE nl.leder_fnr = ?;
                """
            ).use { ps ->
                ps.setString(1, lederFnr)
                ps.executeQuery().toList { toMinSykmeldtDbModel() }
            }
        }
    }

    fun getSykmelding(sykmeldingId: String, lederFnr: String): Pair<SykmeldtDbModel, SykmeldingDbModel>? {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT s.sykmelding_id, s.pasient_fnr, s.orgnummer, s.orgnavn, s.sykmelding, s.lest, s.timestamp, s.latest_tom, sm.pasient_navn, sm.startdato_sykefravaer, sm.latest_tom
                  FROM sykmelding AS s
                    INNER JOIN narmesteleder ON narmesteleder.pasient_fnr = s.pasient_fnr and narmesteleder.orgnummer = s.orgnummer
                    INNER JOIN sykmeldt sm ON narmesteleder.pasient_fnr = sm.pasient_fnr
                WHERE s.sykmelding_id = ? AND narmesteleder.leder_fnr = ?
            """
            ).use { ps ->
                ps.setString(1, sykmeldingId)
                ps.setString(2, lederFnr)
                ps.executeQuery().toSykmeldtSykmelding()
            }
        }
    }

    fun getSoknad(soknadId: String, lederFnr: String): Pair<SykmeldtDbModel, SoknadDbModel>? {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT s.soknad_id,
                   s.sykmelding_id,
                   s.pasient_fnr,
                   s.orgnummer,
                   s.soknad,
                   s.sendt_dato,
                   s.lest,
                   s.timestamp,
                   s.tom,
                   sm.pasient_navn,
                   sm.startdato_sykefravaer,
                   sm.latest_tom
            FROM soknad AS s
                     INNER JOIN narmesteleder n ON s.pasient_fnr = n.pasient_fnr and n.orgnummer = s.orgnummer
                     INNER JOIN sykmeldt sm ON n.pasient_fnr = sm.pasient_fnr
            WHERE s.soknad_id = ?
              AND n.leder_fnr = ?
        """
            ).use { ps ->
                ps.setString(1, soknadId)
                ps.setString(2, lederFnr)
                ps.executeQuery().toSykmeldtSoknad()
            }
        }
    }

    fun getHendelser(lederFnr: String): List<HendelseDbModel> = database.connection.use { connection ->
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

    fun markSykmeldingRead(sykmeldingId: String, lederFnr: String): Boolean {
        return database.connection.use { connection ->
            val updated = connection.prepareStatement(
                """
               UPDATE sykmelding SET lest = TRUE
                FROM narmesteleder
                WHERE (narmesteleder.pasient_fnr = sykmelding.pasient_fnr AND narmesteleder.orgnummer = sykmelding.orgnummer) 
                AND sykmelding.sykmelding_id = ?
                AND narmesteleder.leder_fnr = ?
            """
            ).use { ps ->
                ps.setString(1, sykmeldingId)
                ps.setString(2, lederFnr)
                ps.executeUpdate() > 0
            }
            connection.commit()
            updated
        }
    }

    fun markSoknadRead(soknadId: String, lederFnr: String): Boolean {
        return database.connection.use { connection ->
            val updated = connection.prepareStatement(
                """
               UPDATE soknad SET lest = TRUE
                FROM narmesteleder
                WHERE (narmesteleder.pasient_fnr = soknad.pasient_fnr AND narmesteleder.orgnummer = soknad.orgnummer) 
                AND soknad.soknad_id = ?
                AND narmesteleder.leder_fnr = ?
            """
            ).use { ps ->
                ps.setString(1, soknadId)
                ps.setString(2, lederFnr)
                ps.executeUpdate() > 0
            }
            connection.commit()
            updated
        }
    }

    fun markHendelseRead(hendelseId: UUID, lederFnr: String): Boolean {
        return database.connection.use { connection ->
            val updated = connection.prepareStatement(
                """
               UPDATE hendelser SET ferdigstilt = TRUE, ferdigstilt_timestamp = ?
                FROM narmesteleder
                WHERE (narmesteleder.pasient_fnr = hendelser.pasient_fnr AND narmesteleder.orgnummer = hendelser.orgnummer) 
                AND hendelser.hendelse_id = ?
                AND narmesteleder.leder_fnr = ?
            """
            ).use { ps ->
                ps.setTimestamp(1, Timestamp.from(Instant.now()))
                ps.setObject(2, hendelseId)
                ps.setString(3, lederFnr)
                ps.executeUpdate() > 0
            }
            connection.commit()
            updated
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

private fun ResultSet.toSykmeldtSoknad(): Pair<SykmeldtDbModel, SoknadDbModel>? =
    when (next()) {
        true -> SykmeldtDbModel(
            pasientFnr = getString("pasient_fnr"),
            pasientNavn = getString("pasient_navn"),
            startdatoSykefravaer = getDate("startdato_sykefravaer").toLocalDate(),
            latestTom = getDate("latest_tom").toLocalDate(),
        ) to SoknadDbModel(
            soknadId = getString("soknad_id"),
            sykmeldingId = getString("sykmelding_id"),
            pasientFnr = getString("pasient_fnr"),
            orgnummer = getString("orgnummer"),
            soknad = objectMapper.readValue(getString("soknad")),
            sendtDato = getDate("sendt_dato").toLocalDate(),
            lest = getBoolean("lest"),
            timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
            tom = getDate("tom").toLocalDate(),
        )
        else -> null
    }

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
            sykmeldingId = getString("sykmelding_id"),
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
