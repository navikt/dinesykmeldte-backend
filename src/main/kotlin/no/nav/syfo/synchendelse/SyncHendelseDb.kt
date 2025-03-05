package no.nav.syfo.synchendelse

import no.nav.syfo.application.database.DatabaseInterface

class SyncHendelseDb(private val database: DatabaseInterface) {
    fun markSykmeldingerRead(sykmeldingIds: List<String>) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                UPDATE sykmelding SET lest = true WHERE sykmelding_id = ANY(?);
                """
                        .trimIndent(),
                )
                .use { ps ->
                    ps.setArray(
                        1,
                        connection.createArrayOf("VARCHAR", sykmeldingIds.toTypedArray()),
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
                    UPDATE soknad SET lest = true WHERE soknad_id = ANY(?);
                """
                        .trimIndent(),
                )
                .use { ps ->
                    ps.setArray(
                        1,
                        connection.createArrayOf("VARCHAR", soknadIds.toTypedArray()),
                    )
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
                    UPDATE hendelser SET ferdigstilt = true, ferdigstilt_timestamp = now() WHERE id = ANY(?);
                """
                        .trimIndent(),
                )
                .use { ps ->
                    ps.setArray(
                        1,
                        connection.createArrayOf("VARCHAR", hendelseIds.toTypedArray()),
                    )
                    ps.executeUpdate()
                }
            connection.commit()
        }
    }
}
