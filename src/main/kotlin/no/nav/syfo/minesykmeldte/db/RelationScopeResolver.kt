package no.nav.syfo.minesykmeldte.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.application.metrics.RELATION_SCOPE_DENIED_COUNTER
import no.nav.syfo.pdl.service.PdlIdenterService
import java.sql.Connection
import java.sql.PreparedStatement

class ActiveRelation(
    val id: String,
    val pasientFnr: String,
    val orgnummer: String,
    val lederFnr: String,
)

class RelationScope(
    val relation: ActiveRelation,
    val identer: Set<String>,
    val utvidet: Boolean,
)

class RelationScopeResolver(
    private val database: DatabaseInterface,
    private val identerService: PdlIdenterService,
) {
    suspend fun forLeder(
        lederFnr: String,
        narmestelederId: String? = null,
    ): List<RelationScope> {
        val relations =
            withContext(Dispatchers.IO) {
                database.connection.use { connection ->
                    connection
                        .prepareStatement(
                            """
                            SELECT narmeste_leder_id, pasient_fnr, orgnummer, leder_fnr
                            FROM narmesteleder WHERE leder_fnr = ?
                              AND (?::varchar IS NULL OR narmeste_leder_id = ?)
                            """.trimIndent(),
                        ).use { ps ->
                            ps.setString(1, lederFnr)
                            ps.setString(2, narmestelederId)
                            ps.setString(3, narmestelederId)
                            ps.executeQuery().toList { toActiveRelation() }
                        }
                }
            }
        if (relations.isEmpty()) return emptyList()
        val resolved = identerService.hentIdenter(relations.map { it.pasientFnr }.distinct())
        val allIdenter =
            relations
                .flatMap {
                    resolved[it.pasientFnr] ?: setOf(
                        it.pasientFnr,
                    )
                }.toSet()
        val active = activeRelations(allIdenter, relations.map { it.orgnummer }.toSet())
        return relations.map { relation ->
            forRelation(
                relation,
                resolved[relation.pasientFnr] ?: setOf(relation.pasientFnr),
                active,
            )
        }
    }

    suspend fun forDocument(
        pasientFnr: String,
        orgnummer: String,
        lederFnr: String,
    ): RelationScope? {
        val hasRelation =
            withContext(Dispatchers.IO) {
                database.connection.use { connection ->
                    connection
                        .prepareStatement(
                            "SELECT 1 FROM narmesteleder WHERE leder_fnr = ? AND orgnummer = ? LIMIT 1",
                        ).use { ps ->
                            ps.setString(1, lederFnr)
                            ps.setString(2, orgnummer)
                            ps.executeQuery().use { it.next() }
                        }
                }
            }
        if (!hasRelation) return null
        val identer =
            identerService.hentIdenter(listOf(pasientFnr))[pasientFnr] ?: setOf(pasientFnr)
        val relations = activeRelations(identer, orgnummer)
        return relations
            .filter { it.lederFnr == lederFnr }
            .map { forRelation(it, identer, relations) }
            .firstOrNull { pasientFnr in it.identer }
    }

    /**
     * Reusable for lists: pass only the identer resolved for this relation's own pasientFnr,
     * and all active relations for those identer in its org, never a union across persons.
     */
    fun forRelation(
        relation: ActiveRelation,
        identer: Set<String>,
        activeRelations: List<ActiveRelation>,
    ): RelationScope {
        val matching =
            activeRelations.filter {
                it.orgnummer == relation.orgnummer &&
                    it.pasientFnr in identer
            }
        val onlyRelation =
            relation.pasientFnr in identer && matching.singleOrNull()?.id == relation.id
        if (!onlyRelation && matching.size > 1) RELATION_SCOPE_DENIED_COUNTER.inc()
        return RelationScope(
            relation,
            if (onlyRelation) identer else setOf(relation.pasientFnr),
            onlyRelation && identer.size > 1,
        )
    }

    suspend fun activeRelations(
        identer: Set<String>,
        orgnummer: String,
    ): List<ActiveRelation> = activeRelations(identer, setOf(orgnummer))

    private suspend fun activeRelations(
        identer: Set<String>,
        orgnumre: Set<String>,
    ): List<ActiveRelation> =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                val array = connection.createArrayOf("varchar", identer.toTypedArray())
                val orgArray = connection.createArrayOf("varchar", orgnumre.toTypedArray())
                try {
                    connection
                        .prepareStatement(
                            """
                            SELECT narmeste_leder_id, pasient_fnr, orgnummer, leder_fnr
                            FROM narmesteleder WHERE orgnummer = ANY(?) AND pasient_fnr = ANY(?)
                            """.trimIndent(),
                        ).use { ps ->
                            ps.setArray(1, orgArray)
                            ps.setArray(2, array)
                            ps.executeQuery().toList { toActiveRelation() }
                        }
                } finally {
                    array.free()
                    orgArray.free()
                }
            }
        }
}

private fun java.sql.ResultSet.toActiveRelation() =
    ActiveRelation(
        getString("narmeste_leder_id"),
        getString("pasient_fnr"),
        getString("orgnummer"),
        getString("leder_fnr"),
    )

// Final statement rechecks both ownership and D2 using the same statement snapshot as the document.
internal const val RELATION_SCOPE_SQL = """
    WITH scope_rows AS (
        SELECT * FROM unnest(?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[], ?::boolean[])
            AS r(relation_id, orgnummer, pasient_fnr, ident, utvidet)
    ), scopes AS (
        SELECT relation_id, orgnummer, pasient_fnr, utvidet, array_agg(ident) AS identer
        FROM scope_rows GROUP BY relation_id, orgnummer, pasient_fnr, utvidet
    ), scope AS (
        SELECT n.*, s.identer
        FROM narmesteleder n
        JOIN scopes s ON n.narmeste_leder_id = s.relation_id
            AND n.orgnummer = s.orgnummer AND n.pasient_fnr = s.pasient_fnr
        WHERE n.leder_fnr = ?
          AND (NOT s.utvidet OR (
              SELECT COUNT(*) FROM narmesteleder active
              WHERE active.orgnummer = n.orgnummer AND active.pasient_fnr = ANY(s.identer)
          ) = 1)
    )
"""

internal inline fun <T> Connection.withScope(
    sql: String,
    scope: RelationScope,
    lederFnr: String,
    block: (PreparedStatement) -> T,
): T = withScopes(sql, listOf(scope), lederFnr, block)

internal inline fun <T> Connection.withScopes(
    sql: String,
    scopes: List<RelationScope>,
    lederFnr: String,
    block: (PreparedStatement) -> T,
): T {
    // Flatten once so every parallel array has the same order and length.
    val rows = scopes.flatMap { scope -> scope.identer.map { scope to it } }
    val arrays = mutableListOf<java.sql.Array>()
    try {
        arrays.add(createArrayOf("varchar", rows.map { it.first.relation.id }.toTypedArray()))
        arrays.add(
            createArrayOf("varchar", rows.map { it.first.relation.orgnummer }.toTypedArray()),
        )
        arrays.add(
            createArrayOf("varchar", rows.map { it.first.relation.pasientFnr }.toTypedArray()),
        )
        arrays.add(createArrayOf("varchar", rows.map { it.second }.toTypedArray()))
        arrays.add(createArrayOf("boolean", rows.map { it.first.utvidet }.toTypedArray()))
        return prepareStatement(RELATION_SCOPE_SQL + sql).use { ps ->
            arrays.forEachIndexed { index, array -> ps.setArray(index + 1, array) }
            ps.setString(6, lederFnr)
            block(ps)
        }
    } finally {
        arrays.forEach { it.free() }
    }
}
