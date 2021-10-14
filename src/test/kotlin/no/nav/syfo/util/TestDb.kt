package no.nav.syfo.util

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.ResultSet

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDb {
    companion object {
        val database: DatabaseInterface
        val psqlContainer = PsqlContainer()
            .withExposedPorts(5432)
            .withUsername("username")
            .withPassword("password")
            .withDatabaseName("database")
            .withInitScript("db/testdb-init.sql")

        init {
            psqlContainer.start()
            val mockEnv = mockk<Environment>(relaxed = true)
            every { mockEnv.databaseUsername } returns "username"
            every { mockEnv.databasePassword } returns "password"
            every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl
            database = Database(mockEnv)
        }

        fun clearAllData() {
            return database.connection.use {
                it.prepareStatement(
                    """
                    delete from narmesteleder;
                """
                ).use { ps ->
                    ps.executeUpdate()
                }
                it.commit()
            }
        }

        fun getNarmesteleder(pasientFnr: String): List<NarmestelederDbModel> {
            return database.connection.use {
                it.prepareStatement(
                    """
                    select * from narmesteleder where pasient_fnr = ?;
                """
                ).use { ps ->
                    ps.setString(1, pasientFnr)
                    ps.executeQuery().toList { toNarmestelederDbModel() }
                }
            }
        }

        private fun ResultSet.toNarmestelederDbModel(): NarmestelederDbModel =
            NarmestelederDbModel(
                narmestelederId = getString("narmeste_leder_id"),
                pasientFnr = getString("pasient_fnr"),
                lederFnr = getString("leder_fnr"),
                orgnummer = getString("orgnummer")
            )
    }
}
