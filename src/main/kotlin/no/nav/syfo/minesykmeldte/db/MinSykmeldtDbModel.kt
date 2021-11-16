package no.nav.syfo.minesykmeldte.db

import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import java.time.LocalDate
import java.util.UUID

data class MinSykmeldtDbModel(
    val narmestelederId: String,
    val sykmeldtFnr: String,
    val orgnummer: String,
    val sykmeldtNavn: String,
    val startDatoSykefravar: LocalDate,
    val sykmeldingId: UUID,
    val orgNavn: String,
    val sykmelding: ArbeidsgiverSykmelding,
    val lestSykmelding: Boolean,
    val soknad: SykepengesoknadDTO?,
    val lestSoknad: Boolean,
)
