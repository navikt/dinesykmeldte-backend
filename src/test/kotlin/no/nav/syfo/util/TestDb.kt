package no.nav.syfo.util

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.Environment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.narmesteleder.db.toNarmestelederDbModel
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.ResultSet
import java.time.LocalDate
import java.time.ZoneOffset

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDb private constructor() {
    companion object {
        val database: DatabaseInterface
        private val psqlContainer: PsqlContainer = PsqlContainer()
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
                    DELETE FROM narmesteleder;
                    DELETE FROM sykmelding;
                    DELETE FROM sykmeldt;
                    DELETE FROM soknad;
                    DELETE FROM hendelser;
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
                    SELECT * FROM narmesteleder WHERE pasient_fnr = ?;
                """
                ).use { ps ->
                    ps.setString(1, pasientFnr)
                    ps.executeQuery().toList { toNarmestelederDbModel() }
                }
            }
        }

        fun getSykmeldt(fnr: String): SykmeldtDbModel? {
            return database.connection.use {
                it.prepareStatement(
                    """
                    SELECT * FROM sykmeldt WHERE pasient_fnr = ?;
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
                    SELECT * FROM sykmelding WHERE sykmelding_id = ?;
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
                latestTom = getObject("latest_tom", LocalDate::class.java),
            )

        fun getSoknad(soknadId: String): SoknadDbModel? {
            return database.connection.use {
                it.prepareStatement(
                    """
                    SELECT * FROM soknad WHERE soknad_id = ?;
                """
                ).use { ps ->
                    ps.setString(1, soknadId)
                    ps.executeQuery().toList { toSoknadDbModel() }.firstOrNull()
                }
            }
        }

        private fun ResultSet.toSoknadDbModel(): SoknadDbModel =
            SoknadDbModel(
                soknadId = getString("soknad_id"),
                sykmeldingId = getString("sykmelding_id"),
                pasientFnr = getString("pasient_fnr"),
                orgnummer = getString("orgnummer"),
                soknad = objectMapper.readValue(getString("soknad"), SykepengesoknadDTO::class.java),
                sendtDato = getObject("sendt_dato", LocalDate::class.java),
                lest = getBoolean("lest"),
                timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                tom = getObject("tom", LocalDate::class.java)
            )

        fun getHendelse(id: String): HendelseDbModel? {
            return database.connection.use {
                it.prepareStatement(
                    """
                    SELECT * FROM hendelser WHERE id=?;
                """
                ).use { ps ->
                    ps.setString(1, id)
                    ps.executeQuery().toList { toHendelseDbModel() }.firstOrNull()
                }
            }
        }

        private fun ResultSet.toHendelseDbModel(): HendelseDbModel =
            HendelseDbModel(
                id = getString("id"),
                pasientFnr = getString("pasient_fnr"),
                orgnummer = getString("orgnummer"),
                oppgavetype = getString("oppgavetype"),
                lenke = getString("lenke"),
                tekst = getString("tekst"),
                timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                utlopstidspunkt = getTimestamp("utlopstidspunkt")?.toInstant()?.atOffset(ZoneOffset.UTC),
                ferdigstilt = getBoolean("ferdigstilt"),
                ferdigstiltTimestamp = getTimestamp("ferdigstilt_timestamp")?.toInstant()?.atOffset(ZoneOffset.UTC)
            )
    }
}
