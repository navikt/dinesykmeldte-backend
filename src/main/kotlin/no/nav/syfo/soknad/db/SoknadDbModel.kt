package no.nav.syfo.soknad.db

import no.nav.syfo.soknad.model.Soknad
import no.nav.syfo.util.objectMapper
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.OffsetDateTime

data class SoknadDbModel(
    val soknadId: String,
    val sykmeldingId: String?,
    val pasientFnr: String,
    val orgnummer: String,
    val sykepengesoknad: Soknad,
    val sendtDato: LocalDate?,
    val lest: Boolean,
    val timestamp: OffsetDateTime,
    val tom: LocalDate,
)

fun Any.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }
