package no.nav.syfo.synchendelse

import no.nav.syfo.application.database.DatabaseInterface

class SyncHendelseDb(private val database: DatabaseInterface) {
    fun markSykmeldingerRead(sykmeldingIDs: List<String>) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                        update sykmelding set lest = true where sykmelding_id = ANY(?);
                    """
                        .trimIndent()
                )
                .use { ps ->
                    ps.setArray(
                        1,
                        connection.createArrayOf("VARCHAR", sykmeldingIDs.toTypedArray())
                    )
                    ps.executeUpdate()
                }
            connection.commit()
        }
    }

    fun markSoknadRead(soknadIds: List<String>) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                        update soknad set lest = true where soknad_id = ANY(?);
                    """
                        .trimIndent()
                )
                .use { ps ->
                    ps.setArray(1, connection.createArrayOf("VARCHAR", soknadIds.toTypedArray()))
                    ps.executeUpdate()
                }
            connection.commit()
        }
    }

    fun markHendelserRead(hendelseIds: List<String>) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                        update hendelser set ferdigstilt = true, ferdigstilt_timestamp = now() where id = ANY(?);
                    """
                        .trimIndent()
                )
                .use { ps ->
                    ps.setArray(1, connection.createArrayOf("VARCHAR", hendelseIds.toTypedArray()))
                    ps.executeUpdate()
                }
            connection.commit()
        }
    }
}
