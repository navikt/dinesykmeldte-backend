package no.nav.syfo.util

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.ResultSet
import java.time.LocalDate
import java.time.ZoneOffset

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
                    delete from sykmelding;
                    delete from sykmeldt;
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

        fun getSykmeldt(fnr: String): SykmeldtDbModel? {
            return database.connection.use {
                it.prepareStatement(
                    """
                    select * from sykmeldt where pasient_fnr = ?;
                """
                ).use { ps ->
                    ps.setString(1, fnr)
                    ps.executeQuery().toList { toSykmeldtDbModel() }.firstOrNull()
                }
            }
        }

        private fun ResultSet.toSykmeldtDbModel(): SykmeldtDbModel =
            SykmeldtDbModel(
                pasientFnr = getString("pasient_fnr"),
                pasientNavn = getString("pasient_navn"),
                startdatoSykefravaer = getObject("startdato_sykefravaer", LocalDate::class.java),
                latestTom = getObject("latest_tom", LocalDate::class.java)
            )

        fun getSykmelding(sykmeldingId: String): SykmeldingDbModel? {
            return database.connection.use {
                it.prepareStatement(
                    """
                    select * from sykmelding where sykmelding_id = ?;
                """
                ).use { ps ->
                    ps.setString(1, sykmeldingId)
                    ps.executeQuery().toList { toSykmeldingDbModel() }.firstOrNull()
                }
            }
        }

        private fun ResultSet.toSykmeldingDbModel(): SykmeldingDbModel =
            SykmeldingDbModel(
                sykmeldingId = getString("sykmelding_id"),
                pasientFnr = getString("pasient_fnr"),
                orgnummer = getString("orgnummer"),
                orgnavn = getString("orgnavn"),
                sykmelding = objectMapper.readValue(getString("sykmelding"), ArbeidsgiverSykmelding::class.java),
                lest = getBoolean("lest"),
                timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                latestTom = getObject("latest_tom", LocalDate::class.java)
            )
    }
}
