package no.nav.syfo.narmesteleder.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import java.sql.ResultSet

class NarmestelederDb(
    private val database: DatabaseInterface,
) {
    fun insertOrUpdate(narmesteleder: NarmestelederLeesahKafkaMessage) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
               insert into narmesteleder(narmeste_leder_id, orgnummer, pasient_fnr, leder_fnr) 
               values (?, ?, ?, ?) on conflict (narmeste_leder_id) do nothing ;
            """,
                ).use { preparedStatement ->
                    preparedStatement.setString(1, narmesteleder.narmesteLederId.toString())
                    preparedStatement.setString(2, narmesteleder.orgnummer)
                    preparedStatement.setString(3, narmesteleder.fnr)
                    preparedStatement.setString(4, narmesteleder.narmesteLederFnr)
                    preparedStatement.executeUpdate()
                }
            connection.commit()
        }
    }

    suspend fun remove(narmestelederId: String) {
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                   delete from narmesteleder where narmeste_leder_id = ?;
                """,
                    ).use { ps ->
                        ps.setString(1, narmestelederId)
                        ps.executeUpdate()
                    }
                connection.commit()
            }
        }
    }

    suspend fun finnNarmestelederkoblinger(
        narmesteLederFnr: String,
        narmestelederId: String? = null,
        orgnummer: String? = null,
    ): List<NarmestelederDbModel> =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
           SELECT narmeste_leder_id, pasient_fnr, leder_fnr, orgnummer
           FROM narmesteleder WHERE leder_fnr = ?
             AND (?::varchar IS NULL OR narmeste_leder_id = ?)
             AND (?::varchar IS NULL OR orgnummer = ?)
        """,
                    ).use {
                        it.setString(1, narmesteLederFnr)
                        it.setString(2, narmestelederId)
                        it.setString(3, narmestelederId)
                        it.setString(4, orgnummer)
                        it.setString(5, orgnummer)
                        it.executeQuery().toList { toNarmestelederDbModel() }
                    }
            }
        }

    suspend fun finnAktiveRelasjoner(
        identer: Set<String>,
        orgnumre: Set<String>,
    ): List<NarmestelederDbModel> {
        if (identer.isEmpty() || orgnumre.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                val arrays = mutableListOf<java.sql.Array>()
                try {
                    arrays.add(connection.createArrayOf("varchar", orgnumre.toTypedArray()))
                    arrays.add(connection.createArrayOf("varchar", identer.toTypedArray()))
                    connection
                        .prepareStatement(
                            """
                            SELECT narmeste_leder_id, pasient_fnr, leder_fnr, orgnummer
                            FROM narmesteleder WHERE orgnummer = ANY(?) AND pasient_fnr = ANY(?)
                            """.trimIndent(),
                        ).use { ps ->
                            arrays.forEachIndexed { index, array -> ps.setArray(index + 1, array) }
                            ps.executeQuery().toList { toNarmestelederDbModel() }
                        }
                } finally {
                    arrays.forEach { it.free() }
                }
            }
        }
    }
}

fun ResultSet.toNarmestelederDbModel(): NarmestelederDbModel =
    NarmestelederDbModel(
        narmestelederId = getString("narmeste_leder_id"),
        pasientFnr = getString("pasient_fnr"),
        lederFnr = getString("leder_fnr"),
        orgnummer = getString("orgnummer"),
    )
