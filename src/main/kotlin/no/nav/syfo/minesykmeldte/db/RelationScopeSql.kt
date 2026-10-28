package no.nav.syfo.minesykmeldte.db

import no.nav.syfo.narmesteleder.model.RelationScope
import java.sql.Connection
import java.sql.PreparedStatement

private const val SCOPE_SQL = """
    WITH scope_identer AS (
        SELECT r.narmeste_leder_id, array_agg(DISTINCT r.ident) AS identer
        FROM unnest(?::varchar[], ?::varchar[]) AS r(narmeste_leder_id, ident)
        GROUP BY r.narmeste_leder_id
    ), scope AS (
        SELECT n.narmeste_leder_id, n.pasient_fnr, n.orgnummer, s.identer
        FROM narmesteleder n
        JOIN scope_identer s ON n.narmeste_leder_id = s.narmeste_leder_id AND n.leder_fnr = ?
        WHERE n.pasient_fnr = ANY(s.identer)
    )
"""

// D2 is already decided in Kotlin. Recheck current ownership in every document statement.
// A second active relation inserted concurrently after scopes are computed can still widen one in-flight
// request; this is accepted.
internal fun <T> Connection.withRelationScopes(
    sql: String,
    scopes: List<RelationScope>,
    lederFnr: String,
    block: (statement: PreparedStatement, firstParameter: Int) -> T,
): T {
    val rows = scopes.flatMap { scope -> scope.identer.map { scope.narmestelederId to it } }
    val arrays = mutableListOf<java.sql.Array>()
    try {
        arrays.add(createArrayOf("varchar", rows.map { it.first }.toTypedArray()))
        arrays.add(createArrayOf("varchar", rows.map { it.second }.toTypedArray()))
        return prepareStatement(SCOPE_SQL + sql).use { ps ->
            var index = 1
            arrays.forEach { ps.setArray(index++, it) }
            ps.setString(index++, lederFnr)
            block(ps, index)
        }
    } finally {
        arrays.forEach { it.free() }
    }
}
