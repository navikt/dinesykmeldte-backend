package no.nav.syfo.common.kafka

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.hendelser.HendelserService
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.soknad.SoknadService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.Instant

class CommonKafkaService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val environment: Environment,
    private val narmestelederService: NarmestelederService,
    private val sykmeldingService: SykmeldingService,
    private val soknadService: SoknadService,
    private val hendelserService: HendelserService,
) {
    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L

    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    log.info("Starting consuming topics")
                    kafkaConsumer.subscribe(
                        listOf(
                            environment.narmestelederLeesahTopic,
                            environment.sendtSykmeldingTopic,
                            environment.sykepengesoknadTopic,
                            environment.hendelserTopic,
                        ),
                    )
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
            val records = kafkaConsumer.poll(Duration.ofSeconds(10))
            records.forEach {
                when (it.topic()) {
                    environment.narmestelederLeesahTopic -> narmestelederService.updateNl(it)
                    environment.sendtSykmeldingTopic -> sykmeldingService.handleSendtSykmeldingKafkaMessage(it)
                    environment.sykepengesoknadTopic -> soknadService.handleSykepengesoknad(it)
                    environment.hendelserTopic -> hendelserService.handleHendelse(it)
                    else -> throw IllegalStateException("Har mottatt melding på ukjent topic: ${it.topic()}")
                }
            }
            processedMessages += records.count()
            processedMessages = logProcessedMessages(processedMessages)
        }
    }

    private fun logProcessedMessages(processedMessages: Int): Int {
        val currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            log.info("Processed $processedMessages messages")
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }
}
