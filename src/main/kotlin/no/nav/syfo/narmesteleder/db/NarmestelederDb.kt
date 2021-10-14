package no.nav.syfo.narmesteleder.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage

class NarmestelederDb(private val database: DatabaseInterface) {

    fun insertOrUpdate(narmesteleder: NarmestelederLeesahKafkaMessage) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               insert into narmesteleder(narmeste_leder_id, orgnummer, pasient_fnr, leder_fnr) 
               values (?, ?, ?, ?) on conflict (narmeste_leder_id) do nothing ;
            """
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

    fun remove(narmesteleder: NarmestelederLeesahKafkaMessage) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               delete from narmesteleder where narmeste_leder_id = ?;
            """
            ).use { ps ->
                ps.setString(1, narmesteleder.narmesteLederId.toString())
                ps.executeUpdate()
            }
            connection.commit()
        }
    }
}
