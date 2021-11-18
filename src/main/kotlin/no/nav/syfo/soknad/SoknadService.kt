package no.nav.syfo.soknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.metrics.SOKNAD_TOPIC_COUNTER
import no.nav.syfo.kafka.felles.SoknadsstatusDTO
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDb
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.LocalDate

class SoknadService(
    private val soknadDb: SoknadDb
) {
    fun handleSykepengesoknad(record: ConsumerRecord<String, String>) {
        try {
            handleSykepengesoknad(objectMapper.readValue<SykepengesoknadDTO>(record.value()))
        } catch (e: Exception) {
            log.error("Noe gikk galt ved mottak av sykepengesøknad med id ${record.key()}")
            throw e
        }
    }

    fun handleSykepengesoknad(sykepengesoknad: SykepengesoknadDTO) {
        if ((sykepengesoknad.status == SoknadsstatusDTO.SENDT && sykepengesoknad.sendtArbeidsgiver != null) &&
            sykepengesoknad.tom?.isAfter(LocalDate.now().minusMonths(4)) == true
        ) {
            soknadDb.insert(sykepengesoknad.toSoknadDbModel())
        }
        SOKNAD_TOPIC_COUNTER.inc()
    }
}
