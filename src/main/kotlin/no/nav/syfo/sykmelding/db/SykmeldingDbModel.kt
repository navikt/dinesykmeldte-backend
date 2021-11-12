package no.nav.syfo.sykmelding.db

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class SykmeldingDbModel(
    val sykmeldingId: UUID,
    val pasientFnr: String,
    val orgnummer: String,
    val orgnavn: String?,
    val sykmelding: ArbeidsgiverSykmelding,
    val lest: Boolean,
    val timestamp: OffsetDateTime,
    val latestTom: LocalDate,
    val pasientNavn: String,
)

fun ArbeidsgiverSykmelding.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
