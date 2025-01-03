package no.nav.syfo.sykmelding.db

import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.sykmelding.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding

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
