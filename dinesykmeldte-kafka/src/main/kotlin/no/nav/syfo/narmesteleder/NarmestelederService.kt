package no.nav.syfo.narmesteleder

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.metrics.NL_TOPIC_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord

class NarmestelederService(
    private val narmestelederDb: NarmestelederDb,
) {
    fun updateNl(record: ConsumerRecord<String, String>) {
        try {
            updateNl(objectMapper.readValue<NarmestelederLeesahKafkaMessage>(record.value()))
        } catch (e: Exception) {
            log.error("Noe gikk galt ved mottak av oppdatert nærmeste leder med id ${record.key()}")
            throw e
        }
    }

    fun updateNl(narmesteleder: NarmestelederLeesahKafkaMessage) {
        when (narmesteleder.aktivTom) {
            null -> {
                narmestelederDb.insertOrUpdate(narmesteleder)
                NL_TOPIC_COUNTER.labels("ny").inc()
            }
            else -> {
                narmestelederDb.remove(narmesteleder.narmesteLederId.toString())
                NL_TOPIC_COUNTER.labels("avbrutt").inc()
            }
        }
    }
}
