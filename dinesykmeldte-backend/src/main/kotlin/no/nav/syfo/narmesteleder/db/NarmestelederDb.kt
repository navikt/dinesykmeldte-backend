package no.nav.syfo.narmesteleder.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import java.sql.ResultSet

class NarmestelederDb(private val database: DatabaseInterface) {
    fun remove(narmestelederId: String) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               delete from narmesteleder where narmeste_leder_id = ?;
            """
            ).use { ps ->
                ps.setString(1, narmestelederId)
                ps.executeUpdate()
            }
            connection.commit()
        }
    }

    fun finnNarmestelederkoblinger(
        narmesteLederFnr: String,
        narmestelederId: String,
    ): List<NarmestelederDbModel> {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
           SELECT * FROM narmesteleder WHERE leder_fnr = ? AND narmeste_leder_id = ?;
        """
            ).use {
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
        orgnummer = getString("orgnummer")
    )
