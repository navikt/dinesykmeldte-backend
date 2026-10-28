package no.nav.syfo.virksomhet.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.minesykmeldte.db.withRelationScopes
import no.nav.syfo.narmesteleder.model.RelationScope
import java.sql.ResultSet

class VirksomhetDb(
    private val database: DatabaseInterface,
) {
    suspend fun getVirksomheter(
        lederFnr: String,
        scopes: List<RelationScope>,
    ): List<VirksomhetDbModel> =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .withRelationScopes(
                        """
                        SELECT s.orgnavn, s.orgnummer
                        FROM sykmelding s
                        JOIN scope n ON s.orgnummer = n.orgnummer AND s.pasient_fnr = ANY(n.identer)
                        GROUP BY s.orgnavn, s.orgnummer
                        """.trimIndent(),
                        scopes,
                        lederFnr,
                    ) { ps, _ ->
                        ps.executeQuery().toList { toVirksomhetDbModel() }
                    }
            }
        }
}

private fun ResultSet.toVirksomhetDbModel(): VirksomhetDbModel =
    VirksomhetDbModel(
        navn = getString("orgnavn"),
        orgnummer = getString("orgnummer"),
    )
