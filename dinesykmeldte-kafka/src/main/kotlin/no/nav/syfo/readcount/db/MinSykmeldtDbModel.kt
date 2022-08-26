package no.nav.syfo.readcount.db

import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

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
)

data class HendelseDbModel(
    val id: String,
    val hendelseId: UUID,
    val pasientFnr: String,
    val orgnummer: String,
    val oppgavetype: String,
    val lenke: String?,
    val tekst: String?,
    val timestamp: OffsetDateTime,
    val utlopstidspunkt: OffsetDateTime?,
    val ferdigstilt: Boolean,
    val ferdigstiltTimestamp: OffsetDateTime?
)
