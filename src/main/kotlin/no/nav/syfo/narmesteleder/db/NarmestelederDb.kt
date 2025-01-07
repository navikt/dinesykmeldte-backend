package no.nav.syfo.narmesteleder.db

import java.sql.ResultSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage

class NarmestelederDb(private val database: DatabaseInterface) {

    fun insertOrUpdate(narmesteleder: NarmestelederLeesahKafkaMessage) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
               insert into narmesteleder(narmeste_leder_id, orgnummer, pasient_fnr, leder_fnr) 
               values (?, ?, ?, ?) on conflict (narmeste_leder_id) do nothing ;
            """,
                )
                .use { preparedStatement ->
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
                    )
                    .use { ps ->
                        ps.setString(1, narmestelederId)
                        ps.executeUpdate()
                    }
                connection.commit()
            }
        }
    }

    suspend fun finnNarmestelederkoblinger(
        narmesteLederFnr: String,
        narmestelederId: String,
    ): List<NarmestelederDbModel> =
        withContext(Dispatchers.IO) {
            database.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
           SELECT * FROM narmesteleder WHERE leder_fnr = ? AND narmeste_leder_id = ?;
        """,
                    )
                    .use {
                        it.setString(1, narmesteLederFnr)
                        it.setString(2, narmestelederId)
                        it.executeQuery().toList { toNarmestelederDbModel() }
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
