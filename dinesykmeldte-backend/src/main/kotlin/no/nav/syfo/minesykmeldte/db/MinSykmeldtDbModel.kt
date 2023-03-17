package no.nav.syfo.minesykmeldte.db

import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import java.time.LocalDate
import java.time.OffsetDateTime

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
    val soknad: SykepengesoknadDTO?,
    val lestSoknad: Boolean,
    val sendtTilArbeidsgiverDato: OffsetDateTime?,
    val egenmeldingsdager: List<LocalDate>?,
)
