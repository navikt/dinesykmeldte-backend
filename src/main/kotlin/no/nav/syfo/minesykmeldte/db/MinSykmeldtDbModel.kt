package no.nav.syfo.minesykmeldte.db

import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.soknad.model.Soknad

data class MinSykmeldtDbModel(
    val narmestelederId: String,
    val sykmeldtFnr: String,
    val orgnummer: String,
    val sykmeldtNavn: String,
    val startDatoSykefravar: LocalDate,
    val sykmeldingId: String,
    val orgNavn: String,
    val sykmelding: ArbeidsgiverSykmelding,
    val lestSykmelding: Boolean,
    val soknad: Soknad?,
    val lestSoknad: Boolean,
    val sendtTilArbeidsgiverDato: OffsetDateTime?,
    val egenmeldingsdager: List<LocalDate>?,
)
