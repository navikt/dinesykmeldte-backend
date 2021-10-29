package no.nav.syfo.soknad.db

import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.OffsetDateTime

data class SoknadDbModel(
    val soknadId: String,
    val sykmeldingId: String?,
    val pasientFnr: String,
    val orgnummer: String,
    val soknad: SykepengesoknadDTO,
    val sendtDato: LocalDate?,
    val lest: Boolean,
    val timestamp: OffsetDateTime,
    val latestTom: LocalDate
)

fun SykepengesoknadDTO.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}