package no.nav.syfo.narmesteleder

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.NL_TOPIC_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class NarmestelederService(
    private val kafkaConsumer: KafkaConsumer<String, NarmestelederLeesahKafkaMessage>,
    private val narmestelederDb: NarmestelederDb,
    private val applicationState: ApplicationState,
    private val narmestelederLeesahTopic: String
) {
    fun start() {
        kafkaConsumer.subscribe(listOf(narmestelederLeesahTopic))
        log.info("Starting consuming topic $narmestelederLeesahTopic")
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(1)).forEach {
                try {
                    updateNl(it.value())
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved mottak av oppdatert nærmeste leder med id ${it.key()}")
                    throw e
                }
            }
            kafkaConsumer.commitSync()
        }
    }

    fun updateNl(narmesteleder: NarmestelederLeesahKafkaMessage) {
        when (narmesteleder.aktivTom) {
            null -> {
                narmestelederDb.insertOrUpdate(narmesteleder)
                NL_TOPIC_COUNTER.labels("ny").inc()
            }
            else -> {
                narmestelederDb.remove(narmesteleder)
                NL_TOPIC_COUNTER.labels("avbrutt").inc()
            }
        }
    }
}
