package no.nav.syfo.sistoppdatert

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.toList
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SistOppdatertDb(private val database: DatabaseInterface) {

    fun updateSendtTilArbeidsgiverDato(sykmeldingId: String, sendtTilArbeidsgiverDato: OffsetDateTime) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE sykmelding SET sendt_til_arbeidsgiver_dato = ? WHERE sykmelding_id = ?;
                """
            ).use {
                it.setTimestamp(1, Timestamp.from(sendtTilArbeidsgiverDato.toInstant()))
                it.setString(2, sykmeldingId)
                it.executeUpdate()
            }
            connection.commit()
        }
    }

    fun updateSistOppdatert(fnr: String, sistOppdatert: LocalDate) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE sykmeldt SET sist_oppdatert = ? WHERE pasient_fnr = ?;
                """
            ).use {
                it.setObject(1, sistOppdatert)
                it.setString(2, fnr)
                it.executeUpdate()
            }
            connection.commit()
        }
    }

    fun getSistOppdatert(fnr: String): SistOppdatert {
        return database.connection.use {
            it.prepareStatement(
                """
                    SELECT max(sm.timestamp) as smtimestamp,
                       max(sk.timestamp) as sktimestamp,
                       max(h.timestamp) as htimestamp
                FROM sykmeldt AS s
                    inner join sykmelding AS sm ON sm.pasient_fnr = s.pasient_fnr
                    left join soknad as sk on sk.pasient_fnr = s.pasient_fnr
                    left JOIN hendelser AS h ON h.pasient_fnr = s.pasient_fnr
                WHERE s.pasient_fnr = ?;
                """
            ).use { ps ->
                ps.setString(1, fnr)
                ps.executeQuery().toList { toSistOppdatert() }.first()
            }
        }
    }

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
            latestTom = getObject("latest_tom", LocalDate::class.java),
            sistOppdatert = getObject("sist_oppdatert", LocalDate::class.java)
        )

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
            sendtTilArbeidsgiverDato = getTimestamp("sendt_til_arbeidsgiver_dato")?.toInstant()?.atOffset(ZoneOffset.UTC)
        )

    private fun ResultSet.toSistOppdatert(): SistOppdatert =
        SistOppdatert(
            sisteTimestampSykmelding = getTimestamp("smtimestamp")?.toInstant()?.atOffset(ZoneOffset.UTC),
            sisteTimestampSoknad = getTimestamp("sktimestamp")?.toInstant()?.atOffset(ZoneOffset.UTC),
            sisteTimestampHendelse = getTimestamp("htimestamp")?.toInstant()?.atOffset(ZoneOffset.UTC)
        )
}

data class SistOppdatert(
    val sisteTimestampSykmelding: OffsetDateTime?,
    val sisteTimestampSoknad: OffsetDateTime?,
    val sisteTimestampHendelse: OffsetDateTime?
)
