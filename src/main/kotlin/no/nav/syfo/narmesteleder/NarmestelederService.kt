package no.nav.syfo.narmesteleder

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.NL_TOPIC_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class NarmestelederService(
    private val kafkaConsumer: KafkaConsumer<String, NarmestelederLeesahKafkaMessage>,
    private val narmestelederDb: NarmestelederDb,
    private val applicationState: ApplicationState,
    private val narmestelederLeesahTopic: String
) {
    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    log.info("Starting consuming topic $narmestelederLeesahTopic")
                    kafkaConsumer.subscribe(listOf(narmestelederLeesahTopic))
                    start()
                } catch (ex: Exception) {
                    log.error("Error running kafka consumer for narmesteleder, unsubscribing and waiting 10 seconds for retry", ex)
                    kafkaConsumer.unsubscribe()
                    delay(10_000)
                }
            }
        }
    }

    private fun start() {
        while (applicationState.ready) {
            val nlskjemas = kafkaConsumer.poll(Duration.ZERO)
            nlskjemas.forEach {
                try {
                    updateNl(it.value())
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved mottak av oppdatert nærmeste leder med id ${it.key()}")
                    throw e
                }
            }

            if (!nlskjemas.isEmpty) {
                kafkaConsumer.commitSync()
            }
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
