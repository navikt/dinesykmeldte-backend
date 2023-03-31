package no.nav.syfo.util

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.Environment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.narmesteleder.db.toNarmestelederDbModel
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.TestDb.Companion.database
import org.postgresql.util.PGobject
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDb private constructor() {
    companion object {
        val database: DatabaseInterface

        private val psqlContainer: PsqlContainer

        init {

            try {
                psqlContainer = PsqlContainer()
                    .withExposedPorts(5432)
                    .withUsername("username")
                    .withPassword("password")
                    .withDatabaseName("database")
                    .withInitScript("db/testdb-init.sql")

                psqlContainer.start()
                val mockEnv = mockk<Environment>(relaxed = true)
                every { mockEnv.databaseUsername } returns "username"
                every { mockEnv.databasePassword } returns "password"
                every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl
                database = Database(mockEnv)
            } catch (ex: Exception) {
                log.error("Error", ex)
                throw ex
            }
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
                """,
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
                """,
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
                """,
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
                latestTom = getObject("latest_tom", LocalDate::class.java),
            )

        fun getSykmelding(sykmeldingId: String): SykmeldingDbModel? {
            return database.connection.use {
                it.prepareStatement(
                    """
                    SELECT * FROM sykmelding WHERE sykmelding_id = ?;
                """,
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
                sykmelding = objectMapper.readValue(getString("sykmelding")),
                lest = getBoolean("lest"),
                timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                latestTom = getObject("latest_tom", LocalDate::class.java),
                sendtTilArbeidsgiverDato = getTimestamp("sendt_til_arbeidsgiver_dato")?.toInstant()?.atOffset(ZoneOffset.UTC),
                egenmeldingsdager = objectMapper.readValue(getString("egenmeldingsdager")),
            )

        fun getSoknad(soknadId: String): SoknadDbModel? {
            return database.connection.use {
                it.prepareStatement(
                    """
                    SELECT * FROM soknad WHERE soknad_id = ?;
                """,
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
                tom = getObject("tom", LocalDate::class.java),
            )

        fun getHendelse(id: String): HendelseDbModel? {
            return database.connection.use {
                it.prepareStatement(
                    """
                    SELECT * FROM hendelser WHERE id=?;
                """,
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
                ferdigstiltTimestamp = getTimestamp("ferdigstilt_timestamp")?.toInstant()?.atOffset(ZoneOffset.UTC),
                hendelseId = UUID.fromString(getString("hendelse_id")),
            )
    }
}

fun DatabaseInterface.insertOrUpdate(id: String, orgnummer: String, fnr: String, narmesteLederFnr: String) {
    this.connection.use { connection ->
        connection.prepareStatement(
            """
               insert into narmesteleder(narmeste_leder_id, orgnummer, pasient_fnr, leder_fnr) 
               values (?, ?, ?, ?) on conflict (narmeste_leder_id) do nothing ;
            """,
        ).use { preparedStatement ->
            preparedStatement.setString(1, id)
            preparedStatement.setString(2, orgnummer)
            preparedStatement.setString(3, fnr)
            preparedStatement.setString(4, narmesteLederFnr)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}
fun Any.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}

fun DatabaseInterface.insertHendelse(hendelseDbModel: HendelseDbModel) {
    this.connection.use { connection ->
        connection.prepareStatement(
            """
                    INSERT INTO hendelser(id, pasient_fnr, orgnummer, oppgavetype, lenke, tekst, timestamp, 
                                          utlopstidspunkt, ferdigstilt, ferdigstilt_timestamp, hendelse_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id, oppgavetype) DO NOTHING;
            """,
        ).use { preparedStatement ->
            preparedStatement.setString(1, hendelseDbModel.id)
            preparedStatement.setString(2, hendelseDbModel.pasientFnr)
            preparedStatement.setString(3, hendelseDbModel.orgnummer)
            preparedStatement.setString(4, hendelseDbModel.oppgavetype)
            preparedStatement.setString(5, hendelseDbModel.lenke)
            preparedStatement.setString(6, hendelseDbModel.tekst)
            preparedStatement.setTimestamp(7, Timestamp.from(hendelseDbModel.timestamp.toInstant()))
            preparedStatement.setTimestamp(8, hendelseDbModel.utlopstidspunkt?.let { Timestamp.from(it.toInstant()) })
            preparedStatement.setBoolean(9, hendelseDbModel.ferdigstilt)
            preparedStatement.setTimestamp(10, hendelseDbModel.ferdigstiltTimestamp?.let { Timestamp.from(it.toInstant()) })
            preparedStatement.setObject(11, hendelseDbModel.hendelseId)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.insertOrUpdate(soknadDbModel: SoknadDbModel) {
    this.connection.use { connection ->
        connection.prepareStatement(
            """
               insert into soknad(
                        soknad_id, 
                        sykmelding_id, 
                        pasient_fnr, 
                        orgnummer, 
                        soknad,
                        sendt_dato, 
                        lest, 
                        timestamp, 
                        tom) 
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?) on CONFLICT(soknad_id) do update 
                        set soknad = excluded.soknad,
                        sykmelding_id = excluded.sykmelding_id,
                        pasient_fnr = excluded.pasient_fnr,
                        orgnummer = excluded.orgnummer,
                        timestamp = excluded.timestamp,
                        sendt_dato = excluded.sendt_dato,
                        lest = excluded.lest,
                        tom = excluded.tom
                    ;
            """,
        ).use { preparedStatement ->
            preparedStatement.setString(1, soknadDbModel.soknadId)
            preparedStatement.setString(2, soknadDbModel.sykmeldingId)
            preparedStatement.setString(3, soknadDbModel.pasientFnr)
            preparedStatement.setString(4, soknadDbModel.orgnummer)
            preparedStatement.setObject(5, soknadDbModel.soknad.toPGObject())
            preparedStatement.setObject(6, soknadDbModel.sendtDato)
            preparedStatement.setBoolean(7, soknadDbModel.lest)
            preparedStatement.setTimestamp(8, Timestamp.from(soknadDbModel.timestamp.toInstant()))
            preparedStatement.setObject(9, soknadDbModel.tom)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}
fun DatabaseInterface.insertOrUpdate(sykmeldingDbModel: SykmeldingDbModel, sykmeldt: SykmeldtDbModel) {
    this.connection.use { connection ->
        connection.insertOrUpdateSykmeldt(sykmeldt)
        connection.prepareStatement(
            """
               insert into sykmelding(
                        sykmelding_id, 
                        pasient_fnr, 
                        orgnummer, 
                        orgnavn, 
                        sykmelding, 
                        lest, 
                        timestamp, 
                        latest_tom,
                        sendt_til_arbeidsgiver_dato) 
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?) 
               on conflict (sykmelding_id) do update 
                    set pasient_fnr = ?,
                        orgnummer = ?,
                        orgnavn = ?,
                        sykmelding = ?,
                        lest = ?,
                        timestamp = ?,
                        latest_tom = ?,
                        sendt_til_arbeidsgiver_dato = ?;
            """,
        ).use { preparedStatement ->
            val sendtTilArbeidsgiverDato =
                if (sykmeldingDbModel.sendtTilArbeidsgiverDato != null) Timestamp.from(sykmeldingDbModel.sendtTilArbeidsgiverDato!!.toInstant()) else null
            preparedStatement.setString(1, sykmeldingDbModel.sykmeldingId)
            // insert
            preparedStatement.setString(2, sykmeldingDbModel.pasientFnr)
            preparedStatement.setString(3, sykmeldingDbModel.orgnummer)
            preparedStatement.setString(4, sykmeldingDbModel.orgnavn)
            preparedStatement.setObject(5, sykmeldingDbModel.sykmelding.toPGObject())
            preparedStatement.setBoolean(6, sykmeldingDbModel.lest)
            preparedStatement.setTimestamp(7, Timestamp.from(sykmeldingDbModel.timestamp.toInstant()))
            preparedStatement.setObject(8, sykmeldingDbModel.latestTom)
            preparedStatement.setTimestamp(9, sendtTilArbeidsgiverDato)
            // update
            preparedStatement.setString(10, sykmeldingDbModel.pasientFnr)
            preparedStatement.setString(11, sykmeldingDbModel.orgnummer)
            preparedStatement.setString(12, sykmeldingDbModel.orgnavn)
            preparedStatement.setObject(13, sykmeldingDbModel.sykmelding.toPGObject())
            preparedStatement.setBoolean(14, sykmeldingDbModel.lest)
            preparedStatement.setTimestamp(15, Timestamp.from(sykmeldingDbModel.timestamp.toInstant()))
            preparedStatement.setObject(16, sykmeldingDbModel.latestTom)
            preparedStatement.setTimestamp(17, sendtTilArbeidsgiverDato)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun remove(sykmeldingId: String) {
    database.connection.use { connection ->
        connection.prepareStatement(
            """
               delete from sykmelding where sykmelding_id = ?;
            """,
        ).use { ps ->
            ps.setString(1, sykmeldingId)
            ps.executeUpdate()
        }
        connection.commit()
    }
}

private fun Connection.insertOrUpdateSykmeldt(sykmeldt: SykmeldtDbModel) {
    this.prepareStatement(
        """
               insert into sykmeldt(pasient_fnr, pasient_navn, startdato_sykefravaer, latest_tom) 
                    values (?, ?, ?, ?) 
               on conflict (pasient_fnr) do update
                set pasient_navn = ?,
                    startdato_sykefravaer = ?,
                    latest_tom = ?;
            """,
    ).use { preparedStatement ->
        preparedStatement.setString(1, sykmeldt.pasientFnr)
        // insert
        preparedStatement.setString(2, sykmeldt.pasientNavn)
        preparedStatement.setObject(3, sykmeldt.startdatoSykefravaer)
        preparedStatement.setObject(4, sykmeldt.latestTom)
        // update
        preparedStatement.setString(5, sykmeldt.pasientNavn)
        preparedStatement.setObject(6, sykmeldt.startdatoSykefravaer)
        preparedStatement.setObject(7, sykmeldt.latestTom)
        preparedStatement.executeUpdate()
    }
}
