package no.nav.syfo.soknad

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.SOKNAD_TOPIC_COUNTER
import no.nav.syfo.kafka.felles.SoknadsstatusDTO
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.log
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class SoknadService(
    private val kafkaConsumer: KafkaConsumer<String, SykepengesoknadDTO>,
    private val soknadDb: SoknadDb,
    private val applicationState: ApplicationState,
    private val sykepengesoknadTopic: String
) {
    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L

    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    log.info("Starting consuming topic $sykepengesoknadTopic")
                    kafkaConsumer.subscribe(listOf(sykepengesoknadTopic))
                    start()
                } catch (ex: Exception) {
                    log.error("Error running kafka consumer, unsubscribing and waiting 10 seconds for retry", ex)
                    kafkaConsumer.unsubscribe()
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun start() {
        var processedMessages = 0
        while (applicationState.ready) {
            val soknader = kafkaConsumer.poll(Duration.ZERO)
            soknader.forEach {
                try {
                    handleSykepengesoknad(it.value())
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved mottak av sykepengesøknad med id ${it.key()}")
                    throw e
                }
            }
            processedMessages += soknader.count()
            processedMessages = logProcessedMessages(processedMessages)
            delay(1)
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

    private fun logProcessedMessages(processedMessages: Int): Int {
        var currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            log.info("Processed $processedMessages messages")
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }
}
