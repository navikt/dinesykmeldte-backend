package no.nav.syfo.soknad.db

import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.soknad.model.Soknad

data class SoknadDbModel(
    val soknadId: String,
    val sykmeldingId: String?,
    val pasientFnr: String,
    val orgnummer: String,
    val soknad: Soknad,
    val sendtDato: LocalDate?,
    val lest: Boolean,
    val timestamp: OffsetDateTime,
    val tom: LocalDate,
)
