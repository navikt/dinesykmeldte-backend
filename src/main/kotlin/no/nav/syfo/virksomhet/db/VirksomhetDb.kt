package no.nav.syfo.virksomhet.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import java.sql.ResultSet

class VirksomhetDb(
    private val database: DatabaseInterface,
) {
    suspend fun getVirksomheter(lederFnr: String): List<VirksomhetDbModel> =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                        SELECT s.orgnavn, s.orgnummer
                        FROM sykmelding s
                        JOIN narmesteleder n ON s.orgnummer = n.orgnummer and s.pasient_fnr = n.pasient_fnr
                        WHERE n.leder_fnr = ?
                        GROUP BY s.orgnavn, s.orgnummer
                        """.trimIndent(),
                    ).use { ps ->
                        ps.setString(1, lederFnr)
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
