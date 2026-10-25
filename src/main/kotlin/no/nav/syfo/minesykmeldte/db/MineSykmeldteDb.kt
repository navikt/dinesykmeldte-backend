package no.nav.syfo.minesykmeldte.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.objectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

data class SykmeldingAndSoknadIds(
    val sykmeldingIds: List<String>,
    val soknadIds: List<String>,
)

class RelationHendelse(
    val narmestelederId: String,
    val hendelse: HendelseDbModel,
)

class MineSykmeldteDb(
    private val database: DatabaseInterface,
    private val relationScopeResolver: RelationScopeResolver,
) {
    suspend fun scopesForLeder(
        lederFnr: String,
        narmestelederId: String? = null,
    ): List<RelationScope> = relationScopeResolver.forLeder(lederFnr, narmestelederId)

    suspend fun getMineSykmeldteWithoutSoknad(
        lederFnr: String,
        narmestelederId: String?,
    ): List<MinSykmeldtDbModel> =
        getMineSykmeldte(lederFnr, scopesForLeder(lederFnr, narmestelederId), withSoknad = false)

    suspend fun getMineSykmeldte(lederFnr: String): List<MinSykmeldtDbModel> =
        getMineSykmeldte(lederFnr, scopesForLeder(lederFnr))

    suspend fun getMineSykmeldte(
        lederFnr: String,
        scopes: List<RelationScope>,
        withSoknad: Boolean = true,
    ): List<MinSykmeldtDbModel> =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .withScopes(
                        """
                SELECT nl.narmeste_leder_id,
                       nl.pasient_fnr,
                       nl.orgnummer,
                       s.pasient_navn,
                       s.startdato_sykefravaer,
                       sm.sykmelding_id,
                       sm.pasient_fnr AS sykmelding_fnr,
                       sm.orgnavn,
                       sm.sykmelding,
                       sm.lest as sykmelding_lest,
                       ${if (withSoknad) "sk.sykepengesoknad, sk.sendt_dato, sk.lest AS soknad_lest," else ""}
                       sm.sendt_til_arbeidsgiver_dato as sendt_til_arbeidsgiver_dato,
                       sm.egenmeldingsdager
                FROM scope AS nl
                    INNER JOIN LATERAL (
                        SELECT s.* FROM sykmeldt s WHERE s.pasient_fnr = ANY(nl.identer)
                        ORDER BY s.latest_tom DESC, s.pasient_fnr LIMIT 1
                    ) s ON TRUE
                    INNER JOIN sykmelding AS sm ON sm.pasient_fnr = ANY(nl.identer) AND sm.orgnummer = nl.orgnummer
                    ${if (withSoknad) "LEFT JOIN soknad sk ON sk.sykmelding_id = sm.sykmelding_id AND sk.pasient_fnr = sm.pasient_fnr" else ""}
                """,
                        scopes,
                        lederFnr,
                    ) { ps ->
                        ps.executeQuery().toList { toMinSykmeldtDbModel(withSoknad) }
                    }
            }
        }

    suspend fun getSykmelding(
        sykmeldingId: String,
        lederFnr: String,
    ): Pair<SykmeldtDbModel, SykmeldingDbModel>? {
        val scope = documentScope(DocumentType.SYKMELDING, sykmeldingId, lederFnr) ?: return null
        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.withScope(
                    """
                SELECT s.sykmelding_id, s.pasient_fnr, s.orgnummer, s.orgnavn,
                       s.sykmelding, s.lest, s.timestamp, s.latest_tom,
                       sm.pasient_navn, sm.startdato_sykefravaer,
                       sm.latest_tom AS sykmeldt_latest_tom,
                       s.sendt_til_arbeidsgiver_dato, s.egenmeldingsdager
                  FROM sykmelding AS s
                    INNER JOIN scope n ON s.pasient_fnr = ANY(n.identer) AND n.orgnummer = s.orgnummer
                    INNER JOIN LATERAL (
                        SELECT sm.* FROM sykmeldt sm WHERE sm.pasient_fnr = ANY(n.identer)
                        ORDER BY sm.latest_tom DESC, sm.pasient_fnr LIMIT 1
                    ) sm ON TRUE
                WHERE s.sykmelding_id = ?
            """,
                    scope,
                    lederFnr,
                ) { ps ->
                    ps.setString(7, sykmeldingId)
                    ps.executeQuery().toSykmeldtSykmelding()
                }
            }
        }
    }

    suspend fun getSoknad(
        soknadId: String,
        lederFnr: String,
    ): Pair<SykmeldtDbModel, SoknadDbModel>? {
        val scope = documentScope(DocumentType.SOKNAD, soknadId, lederFnr) ?: return null
        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection.withScope(
                    """
                SELECT s.soknad_id,
                   s.sykmelding_id,
                   s.pasient_fnr,
                   s.orgnummer,
                   s.sykepengesoknad,
                   s.sendt_dato,
                   s.lest,
                   s.timestamp,
                   s.tom,
                   sm.pasient_navn,
                   sm.startdato_sykefravaer,
                   sm.latest_tom AS sykmeldt_latest_tom
            FROM soknad AS s
                     INNER JOIN scope n ON s.pasient_fnr = ANY(n.identer) AND n.orgnummer = s.orgnummer
                     INNER JOIN LATERAL (
                         SELECT sm.* FROM sykmeldt sm WHERE sm.pasient_fnr = ANY(n.identer)
                         ORDER BY sm.latest_tom DESC, sm.pasient_fnr LIMIT 1
                     ) sm ON TRUE
            WHERE s.soknad_id = ?
        """,
                    scope,
                    lederFnr,
                ) { ps ->
                    ps.setString(7, soknadId)
                    ps.executeQuery().toSykmeldtSoknad()
                }
            }
        }
    }

    suspend fun getHendelser(lederFnr: String): List<RelationHendelse> =
        getHendelser(lederFnr, scopesForLeder(lederFnr))

    suspend fun getHendelser(
        lederFnr: String,
        scopes: List<RelationScope>,
    ): List<RelationHendelse> =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .withScopes(
                        """
           SELECT n.narmeste_leder_id, h.pasient_fnr,
              h.orgnummer,
              h.tekst, 
              h.id, 
              h.oppgavetype,
              h.lenke,
              h.timestamp,
              h.hendelse_id
           FROM hendelser h
                INNER JOIN scope n ON h.pasient_fnr = ANY(n.identer) AND n.orgnummer = h.orgnummer
           WHERE h.ferdigstilt = false and (h.utlopstidspunkt > ? or h.utlopstidspunkt is null)
             AND EXISTS (SELECT 1 FROM sykmeldt sm WHERE sm.pasient_fnr = ANY(n.identer))
        """,
                        scopes,
                        lederFnr,
                    ) { ps ->
                        ps.setTimestamp(7, Timestamp.from(Instant.now()))
                        ps.executeQuery().toList {
                            RelationHendelse(getString("narmeste_leder_id"), toHendelseDbModels())
                        }
                    }
            }
        }

    suspend fun markSykmeldingRead(
        sykmeldingId: String,
        lederFnr: String,
    ): List<String> = markDocumentRead(DocumentType.SYKMELDING, sykmeldingId, lederFnr)

    suspend fun markSoknadRead(
        soknadId: String,
        lederFnr: String,
    ): List<String> = markDocumentRead(DocumentType.SOKNAD, soknadId, lederFnr)

    suspend fun markHendelseRead(
        hendelseId: UUID,
        lederFnr: String,
    ): List<String> = markDocumentRead(DocumentType.HENDELSE, hendelseId, lederFnr)

    private suspend fun documentScope(
        type: DocumentType,
        id: Any,
        lederFnr: String,
    ): RelationScope? {
        val identity =
            withContext(Dispatchers.IO) {
                database.connection.use { connection ->
                    // Table/column names come only from the closed enum, never request data.
                    connection
                        .prepareStatement(
                            "SELECT pasient_fnr, orgnummer FROM ${type.table} WHERE ${type.idColumn} = ?",
                        ).use { ps ->
                            ps.setObject(1, id)
                            ps.executeQuery().use { rs ->
                                if (rs.next()) {
                                    rs.getString(
                                        "pasient_fnr",
                                    ) to rs.getString("orgnummer")
                                } else {
                                    null
                                }
                            }
                        }
                }
            } ?: return null
        return relationScopeResolver.forDocument(identity.first, identity.second, lederFnr)
    }

    private suspend fun markDocumentRead(
        type: DocumentType,
        id: Any,
        lederFnr: String,
    ): List<String> {
        val scope = documentScope(type, id, lederFnr) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                val updated =
                    connection.withScope(
                        """
                        UPDATE ${type.table} d SET ${type.readAssignment}
                        FROM scope n
                        WHERE d.${type.idColumn} = ? AND d.orgnummer = n.orgnummer
                          AND d.pasient_fnr = ANY(n.identer)
                        RETURNING d.${type.idColumn}
                        """.trimIndent(),
                        scope,
                        lederFnr,
                    ) { ps ->
                        ps.setObject(7, id)
                        ps.executeQuery().toList { getString(type.idColumn) }
                    }
                connection.commit()
                updated
            }
        }
    }

    suspend fun markAllSykmeldingAndSoknadAsRead(lederFnr: String): SykmeldingAndSoknadIds =
        markAllSykmeldingAndSoknadAsRead(lederFnr, scopesForLeder(lederFnr))

    suspend fun markAllSykmeldingAndSoknadAsRead(
        lederFnr: String,
        scopes: List<RelationScope>,
    ): SykmeldingAndSoknadIds =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                val sykmeldingIds =
                    connection
                        .withScopes(
                            """
                                 update sykmelding s set lest = TRUE 
                            FROM scope nl WHERE s.pasient_fnr = ANY(nl.identer)
                              AND s.orgnummer = nl.orgnummer RETURNING s.sykmelding_id;

                            """.trimIndent(),
                            scopes,
                            lederFnr,
                        ) { ps ->
                            ps.executeQuery().toList { getString("sykmelding_id") }
                        }
                val soknadIds =
                    connection
                        .withScopes(
                            """
                            update soknad s set lest = TRUE
                            FROM scope nl WHERE s.pasient_fnr = ANY(nl.identer)
                              AND s.orgnummer = nl.orgnummer RETURNING s.soknad_id;
                            """.trimIndent(),
                            scopes,
                            lederFnr,
                        ) { ps ->
                            ps.executeQuery().toList { getString("soknad_id") }
                        }

                connection.commit()
                SykmeldingAndSoknadIds(
                    sykmeldingIds = sykmeldingIds,
                    soknadIds = soknadIds,
                )
            }
        }
}

private enum class DocumentType(
    val table: String,
    val idColumn: String,
    val readAssignment: String,
) {
    SYKMELDING("sykmelding", "sykmelding_id", "lest = TRUE"),
    SOKNAD("soknad", "soknad_id", "lest = TRUE"),
    HENDELSE(
        "hendelser",
        "hendelse_id",
        "ferdigstilt = TRUE, ferdigstilt_timestamp = CURRENT_TIMESTAMP",
    ),
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
        ferdigstiltTimestamp = null,
    )

private fun ResultSet.toSykmeldtSoknad(): Pair<SykmeldtDbModel, SoknadDbModel>? =
    when (next()) {
        true ->
            SykmeldtDbModel(
                pasientFnr = getString("pasient_fnr"),
                pasientNavn = getString("pasient_navn"),
                startdatoSykefravaer = getDate("startdato_sykefravaer").toLocalDate(),
                latestTom = getDate("sykmeldt_latest_tom").toLocalDate(),
                sistOppdatert = null,
            ) to
                SoknadDbModel(
                    soknadId = getString("soknad_id"),
                    sykmeldingId = getString("sykmelding_id"),
                    pasientFnr = getString("pasient_fnr"),
                    orgnummer = getString("orgnummer"),
                    sykepengesoknad = objectMapper.readValue(getString("sykepengesoknad")),
                    sendtDato = getDate("sendt_dato").toLocalDate(),
                    lest = getBoolean("lest"),
                    timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                    tom = getDate("tom").toLocalDate(),
                )
        else -> null
    }

private fun ResultSet.toMinSykmeldtDbModel(withSoknad: Boolean = true): MinSykmeldtDbModel =
    MinSykmeldtDbModel(
        narmestelederId = getString("narmeste_leder_id"),
        sykmeldtFnr = getString("pasient_fnr"),
        sykmeldingFnr = getString("sykmelding_fnr"),
        orgnummer = getString("orgnummer"),
        sykmeldtNavn = getString("pasient_navn"),
        startDatoSykefravar = getDate("startdato_sykefravaer").toLocalDate(),
        sykmeldingId = getString("sykmelding_id"),
        orgNavn = getString("orgnavn"),
        sykmelding = objectMapper.readValue(getString("sykmelding")),
        lestSykmelding = getBoolean("sykmelding_lest"),
        soknad =
            if (withSoknad) {
                getString("sykepengesoknad")?.let { objectMapper.readValue(it) }
            } else {
                null
            },
        lestSoknad = if (withSoknad) getBoolean("soknad_lest") else true,
        sendtTilArbeidsgiverDato =
            getTimestamp("sendt_til_arbeidsgiver_dato")?.toInstant()?.atOffset(ZoneOffset.UTC),
        egenmeldingsdager = getString("egenmeldingsdager")?.let { objectMapper.readValue(it) },
    )

private fun ResultSet.toSykmeldtSykmelding(): Pair<SykmeldtDbModel, SykmeldingDbModel>? =
    when (next()) {
        true ->
            SykmeldtDbModel(
                pasientFnr = getString("pasient_fnr"),
                pasientNavn = getString("pasient_navn"),
                startdatoSykefravaer = getDate("startdato_sykefravaer").toLocalDate(),
                latestTom = getDate("sykmeldt_latest_tom").toLocalDate(),
                sistOppdatert = null,
            ) to
                SykmeldingDbModel(
                    sykmeldingId = getString("sykmelding_id"),
                    pasientFnr = getString("pasient_fnr"),
                    orgnummer = getString("orgnummer"),
                    orgnavn = getString("orgnavn"),
                    sykmelding = objectMapper.readValue(getString("sykmelding")),
                    lest = getBoolean("lest"),
                    timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                    latestTom = getDate("latest_tom").toLocalDate(),
                    sendtTilArbeidsgiverDato =
                        getTimestamp("sendt_til_arbeidsgiver_dato")
                            ?.toInstant()
                            ?.atOffset(ZoneOffset.UTC),
                    egenmeldingsdager =
                        getString("egenmeldingsdager")?.let { objectMapper.readValue(it) },
                )
        false -> null
    }

fun <T> ResultSet.toList(mapper: ResultSet.() -> T): List<T> =
    mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }
