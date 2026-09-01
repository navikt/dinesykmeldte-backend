package no.nav.syfo.common.kafka

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.KAFKA_CONSUMER_RESTART_COUNTER
import no.nav.syfo.application.metrics.KAFKA_PDL_PAUSED_PARTITIONS
import no.nav.syfo.application.metrics.KAFKA_PDL_QUARANTINED_PARTITIONS
import no.nav.syfo.application.metrics.KAFKA_PDL_RETRY_COUNTER
import no.nav.syfo.application.metrics.KAFKA_PDL_TERMINAL_FAILURE_COUNTER
import no.nav.syfo.hendelser.HendelserService
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.exceptions.PdlPersonoppslagFailedException
import no.nav.syfo.soknad.SoknadService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.util.logger
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.MDC
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

class CommonKafkaService(
    private val kafkaConsumer: Consumer<String, String>,
    private val applicationState: ApplicationState,
    private val environment: Environment,
    private val narmestelederService: NarmestelederService,
    private val sykmeldingService: SykmeldingService,
    private val soknadService: SoknadService,
    private val hendelserService: HendelserService,
    private val nanoTime: () -> Long = System::nanoTime,
    private val pdlRetryBackoff: Duration = Duration.ofSeconds(10),
    private val pdlCircuitBreakerCooldown: Duration = Duration.ofMinutes(15),
    private val maxImmediatePdlAttempts: Int = 3,
    private val pdlRetryJitterRatio: Double = 0.1,
) {
    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L
    private val log = logger()
    private val pdlRetryStates = mutableMapOf<TopicPartition, PdlRetryState>()
    private val rebalanceListener = PdlRetryRebalanceListener()

    init {
        require(!pdlRetryBackoff.isNegative && !pdlRetryBackoff.isZero) {
            "PDL retry-backoff må være større enn null"
        }
        require(!pdlCircuitBreakerCooldown.isNegative && !pdlCircuitBreakerCooldown.isZero) {
            "PDL circuit-breaker cooldown må være større enn null"
        }
        require(maxImmediatePdlAttempts > 0) { "PDL retry-budget må være større enn null" }
        require(pdlRetryJitterRatio in 0.0..0.5) {
            "PDL retry-jitter må være mellom 0.0 og 0.5"
        }
    }

    fun kafkaWakeup() = kafkaConsumer.wakeup()

    suspend fun startConsumer() =
        coroutineScope {
            try {
                while (isActive && applicationState.ready) {
                    try {
                        val topics = determineTopics()
                        log.info("Starting consuming topics: $topics")

                        kafkaConsumer.subscribe(topics, rebalanceListener)
                        start()
                    } catch (ex: WakeupException) {
                        log.info("Kafka consumer received wakeup signal, shutting down")
                        break
                    } catch (ex: CancellationException) {
                        kafkaConsumer.unsubscribe()
                        throw ex
                    } catch (ex: Exception) {
                        log.warn(
                            "Error running kafka consumer, unsubscribing and waiting 10 seconds for retry",
                            ex,
                        )
                        restartConsumerAfterFailure()
                    }
                }
            } finally {
                clearPdlRetryStates()
                try {
                    kafkaConsumer.close(Duration.ofSeconds(3))
                } catch (ex: Exception) {
                    log.warn("Error closing kafka consumer during shutdown", ex)
                }
            }
        }

    private suspend fun restartConsumerAfterFailure() {
        kafkaConsumer.unsubscribe()
        clearPdlRetryStates()
        KAFKA_CONSUMER_RESTART_COUNTER.inc()
        kotlinx.coroutines.delay(10_000)
    }

    private fun determineTopics(): List<String> {
        val topics =
            mutableListOf(
                environment.sendtSykmeldingTopic,
                environment.sykepengesoknadTopic,
                environment.hendelserTopic,
            )
        if (environment.consumeTeamsykmeldingNlLeesahTopic) {
            topics.add(environment.narmestelederLeesahTopic)
        }
        if (environment.consumeTeamEsyfoNlLeesahTopic) {
            topics.add(environment.syfoNarmestelederLeesahTopic)
        }
        return topics
    }

    private fun consumingBothLeesahTopics(): Boolean =
        environment.consumeTeamsykmeldingNlLeesahTopic && environment.consumeTeamEsyfoNlLeesahTopic

    private suspend fun start() {
        var processedMessages = 0
        while (applicationState.ready) {
            resumeDuePdlPartitions()
            val records = kafkaConsumer.poll(nextPollTimeout())
            for (record in records) {
                if (!applicationState.ready) break
                if (processRecord(record)) {
                    commitRecord(record)
                    clearPdlRetryState(record)
                    processedMessages++
                }
            }
            processedMessages = logProcessedMessages(processedMessages)
        }
    }

    private suspend fun processRecord(record: ConsumerRecord<String, String>): Boolean =
        withKafkaRecordContext(record) {
            try {
                when (record.topic()) {
                    environment.narmestelederLeesahTopic -> {
                        narmestelederService.updateNl(
                            record = record,
                            incrementMetrics = true,
                        )
                        log.info(
                            "Recieved message on ${environment.narmestelederLeesahTopic}",
                        )
                    }

                    environment.syfoNarmestelederLeesahTopic -> {
                        narmestelederService.updateNl(
                            record = record,
                            incrementMetrics = !consumingBothLeesahTopics(),
                        )
                        log.info(
                            "Recieved message on ${environment.syfoNarmestelederLeesahTopic}",
                        )
                    }

                    environment.sendtSykmeldingTopic ->
                        sykmeldingService.handleSendtSykmeldingKafkaMessage(record)

                    environment.sykepengesoknadTopic -> soknadService.handleSykepengesoknad(record)
                    environment.hendelserTopic -> hendelserService.handleHendelse(record)
                    else ->
                        throw IllegalStateException(
                            "Har mottatt melding på ukjent topic: ${record.topic()}",
                        )
                }
                true
            } catch (_: NameNotFoundInPdlException) {
                true
            } catch (exception: PdlPersonoppslagFailedException) {
                schedulePdlRetry(record, retryable = exception.retryable)
                false
            }
        }

    private fun schedulePdlRetry(
        record: ConsumerRecord<String, String>,
        retryable: Boolean,
    ) {
        val topicPartition = TopicPartition(record.topic(), record.partition())
        val previousState =
            pdlRetryStates[topicPartition]?.takeIf { it.offset == record.offset() }
        val attempt = (previousState?.attempt ?: 0) + 1

        kafkaConsumer.seek(topicPartition, record.offset())
        kafkaConsumer.pause(setOf(topicPartition))

        if (previousState?.phase == PdlRetryPhase.HALF_OPEN) {
            pdlRetryStates[topicPartition] =
                PdlRetryState(
                    offset = record.offset(),
                    attempt = attempt,
                    phase = PdlRetryPhase.TERMINAL,
                    resumeAtNanos = null,
                )
            updatePausedPartitionsMetric()
            KAFKA_PDL_TERMINAL_FAILURE_COUNTER.inc()
            log
                .atError()
                .addKeyValue("event_type", "kafka_pdl_retry_exhausted")
                .addKeyValue("retry_attempt", attempt)
                .addKeyValue("retryable", retryable)
                .addKeyValue("terminal_action", "partition_quarantined")
                .log("PDL-retry er brukt opp; Kafka-partisjon er satt i karantene")
            return
        }

        val circuitOpen =
            !retryable ||
                attempt >= maxImmediatePdlAttempts
        val retryPhase =
            if (circuitOpen) PdlRetryPhase.OPEN else PdlRetryPhase.FAST_BACKOFF
        val baseCooldown =
            if (circuitOpen) pdlCircuitBreakerCooldown else pdlRetryBackoff
        val cooldown = baseCooldown.withJitter()
        val resumeAtNanos = nanoTime() + cooldown.toNanos()

        pdlRetryStates[topicPartition] =
            PdlRetryState(
                offset = record.offset(),
                attempt = attempt,
                phase = retryPhase,
                resumeAtNanos = resumeAtNanos,
            )
        updatePausedPartitionsMetric()

        val retryState = if (circuitOpen) "circuit_open" else "short_backoff"
        KAFKA_PDL_RETRY_COUNTER.labels(retryState).inc()
        val logBuilder =
            if (circuitOpen) log.atWarn() else log.atInfo()
        logBuilder
            .addKeyValue("event_type", "kafka_pdl_retry_scheduled")
            .addKeyValue("retry_state", retryState)
            .addKeyValue("retry_attempt", attempt)
            .addKeyValue("retryable", retryable)
            .addKeyValue("retry_delay_seconds", cooldown.seconds)
            .log("PDL-retry planlagt for Kafka-partisjon")
    }

    private fun resumeDuePdlPartitions() {
        val currentTime = nanoTime()
        val assignment = kafkaConsumer.assignment()
        val duePartitions =
            pdlRetryStates
                .filterValues { state ->
                    state.resumeAtNanos?.let { it <= currentTime } == true
                }.keys
                .intersect(assignment)
        if (duePartitions.isEmpty()) return

        kafkaConsumer.resume(duePartitions)
        duePartitions.forEach { topicPartition ->
            val state = pdlRetryStates.getValue(topicPartition)
            pdlRetryStates[topicPartition] =
                state.copy(
                    phase =
                        if (state.phase == PdlRetryPhase.OPEN) {
                            PdlRetryPhase.HALF_OPEN
                        } else {
                            state.phase
                        },
                    resumeAtNanos = null,
                )
        }
        updatePausedPartitionsMetric()
        log
            .atInfo()
            .addKeyValue("event_type", "kafka_pdl_partitions_resumed")
            .addKeyValue("partition_count", duePartitions.size)
            .log("Kafka-partisjoner åpnet for nytt PDL-forsøk")
    }

    private fun nextPollTimeout(): Duration {
        val currentTime = nanoTime()
        val assignment = kafkaConsumer.assignment()
        val nextResumeAt =
            pdlRetryStates
                .filterKeys { it in assignment }
                .values
                .mapNotNull(PdlRetryState::resumeAtNanos)
                .minOrNull()
        val untilNextResume =
            nextResumeAt?.let { resumeAt ->
                Duration.ofNanos((resumeAt - currentTime).coerceAtLeast(0))
            }
        return if (untilNextResume == null || untilNextResume > KAFKA_POLL_TIMEOUT) {
            KAFKA_POLL_TIMEOUT
        } else {
            untilNextResume
        }
    }

    private fun commitRecord(record: ConsumerRecord<String, String>) {
        val topicPartition = TopicPartition(record.topic(), record.partition())
        kafkaConsumer.commitSync(
            mapOf(topicPartition to OffsetAndMetadata(record.offset() + 1)),
        )
    }

    private fun clearPdlRetryState(record: ConsumerRecord<String, String>) {
        val topicPartition = TopicPartition(record.topic(), record.partition())
        val removedState = pdlRetryStates.remove(topicPartition)
        updatePausedPartitionsMetric()
        if (removedState?.phase == PdlRetryPhase.HALF_OPEN) {
            log
                .atInfo()
                .addKeyValue("event_type", "kafka_pdl_circuit_closed")
                .addKeyValue("retry_attempt", removedState.attempt)
                .addKeyValue("kafka_topic", record.topic())
                .addKeyValue("kafka_partition", record.partition().toString())
                .addKeyValue("kafka_offset", record.offset().toString())
                .log("PDL circuit lukket etter vellykket Kafka-behandling")
        }
    }

    private fun clearPdlRetryStates() {
        pdlRetryStates.clear()
        updatePausedPartitionsMetric()
    }

    private fun updatePausedPartitionsMetric() {
        val retryStates = pdlRetryStates.values
        KAFKA_PDL_PAUSED_PARTITIONS.set(retryStates.count(PdlRetryState::isPaused).toDouble())
        KAFKA_PDL_QUARANTINED_PARTITIONS.set(
            retryStates.count { it.phase == PdlRetryPhase.TERMINAL }.toDouble(),
        )
    }

    private fun Duration.withJitter(): Duration {
        if (isZero || pdlRetryJitterRatio == 0.0) return this
        val factor =
            ThreadLocalRandom.current().nextDouble(
                1.0 - pdlRetryJitterRatio,
                1.0 + pdlRetryJitterRatio,
            )
        return Duration.ofMillis((toMillis() * factor).toLong().coerceAtLeast(1))
    }

    private inner class PdlRetryRebalanceListener : ConsumerRebalanceListener {
        override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
            removeRetryStates(partitions)
        }

        override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
            val pausedAssignedPartitions =
                pdlRetryStates
                    .filterValues(PdlRetryState::isPaused)
                    .keys
                    .intersect(kafkaConsumer.assignment())
            if (pausedAssignedPartitions.isNotEmpty()) {
                kafkaConsumer.pause(pausedAssignedPartitions)
            }
            updatePausedPartitionsMetric()
        }

        override fun onPartitionsLost(partitions: Collection<TopicPartition>) {
            removeRetryStates(partitions)
        }

        private fun removeRetryStates(partitions: Collection<TopicPartition>) {
            partitions.forEach(pdlRetryStates::remove)
            updatePausedPartitionsMetric()
        }
    }

    private suspend fun <T> withKafkaRecordContext(
        record: ConsumerRecord<String, String>,
        block: suspend () -> T,
    ): T {
        val contextMap =
            MDC.getCopyOfContextMap().orEmpty() +
                mapOf(
                    "kafka_topic" to record.topic(),
                    "kafka_partition" to record.partition().toString(),
                    "kafka_offset" to record.offset().toString(),
                )
        return withContext(MDCContext(contextMap)) { block() }
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

private val KAFKA_POLL_TIMEOUT: Duration = Duration.ofSeconds(10)

private data class PdlRetryState(
    val offset: Long,
    val attempt: Int,
    val phase: PdlRetryPhase,
    val resumeAtNanos: Long?,
) {
    fun isPaused(): Boolean = resumeAtNanos != null || phase == PdlRetryPhase.TERMINAL
}

private enum class PdlRetryPhase {
    FAST_BACKOFF,
    OPEN,
    HALF_OPEN,
    TERMINAL,
}
