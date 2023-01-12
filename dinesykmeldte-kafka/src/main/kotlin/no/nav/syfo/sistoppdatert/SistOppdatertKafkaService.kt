package no.nav.syfo.sistoppdatert

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SistOppdatertKafkaService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val sendtSykmeldingTopic: String,
    private val sistOppdatertService: SistOppdatertService
) {
    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L

    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    log.info("Starting consuming sendt sykmelding topic")
                    kafkaConsumer.subscribe(
                        listOf(
                            sendtSykmeldingTopic
                        )
                    )
                    start()
                } catch (ex: Exception) {
                    log.error("Error running kafka consumer for sendt sykmelding, unsubscribing and waiting 10 seconds for retry", ex)
                    kafkaConsumer.unsubscribe()
                    delay(10_000)
                }
            }
        }
    }

    private fun start() {
        var processedMessages = 0
        var done = false
        while (applicationState.ready && !done) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(10))
            records.forEach {
                if (it.timestamp() < OffsetDateTime.of(2023, 1, 11, 5, 0, 0, 0, ZoneOffset.UTC).toEpochSecond()) {
                    sistOppdatertService.handleSendtSykmeldingKafkaMessage(it)
                } else {
                    log.info("Ferdig med alle gamle sendte sykmeldinger, stopper jobb..")
                    done = true
                }
            }
            processedMessages += records.count()
            processedMessages = logProcessedMessages(processedMessages)
        }
    }

    private fun logProcessedMessages(processedMessages: Int): Int {
        val currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            log.info("Processed $processedMessages messages (SistOppdatertService)")
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }
}
