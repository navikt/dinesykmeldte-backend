package no.nav.syfo.sykmelding.db

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import java.time.LocalDate
import java.time.OffsetDateTime

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
)
