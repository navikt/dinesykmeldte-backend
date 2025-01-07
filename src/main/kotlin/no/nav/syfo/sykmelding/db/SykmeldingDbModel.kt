package no.nav.syfo.sykmelding.db

import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.sykmelding.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.util.objectMapper
import org.postgresql.util.PGobject

data class SykmeldingDbModel(
    val sykmeldingId: String,
    val pasientFnr: String,
    val orgnummer: String,
    val orgnavn: String?,
    val sykmelding: ArbeidsgiverSykmelding,
    val lest: Boolean,
    val timestamp: OffsetDateTime,
    val latestTom: LocalDate,
    val sendtTilArbeidsgiverDato: OffsetDateTime?,
    val egenmeldingsdager: List<LocalDate>?,
)

fun ArbeidsgiverSykmelding.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }

fun List<LocalDate>.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }
