package no.nav.syfo.sykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.syfo.Environment
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.syketilfelle.client.SyketilfelleNotFoundException
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class UpdateSykmeldtService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val sykmeldingService: SykmeldingService,
    private val environment: Environment
) {
    var lastTimestamp = OffsetDateTime.MIN
    var shouldRun = true

    @OptIn(DelicateCoroutinesApi::class)
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (shouldRun) {
                log.info("last timestamp: $lastTimestamp")
                delay(Duration.ofMinutes(10).toMillis())
            }
            log.info("last timestamp: $lastTimestamp")
        }
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (shouldRun) {
                try {
                    log.info("Starting consuming topics")
                    kafkaConsumer.subscribe(
                        listOf(
                            environment.sendtSykmeldingTopic,
                        )
                    )
                    start()
                    shouldRun = false
                } catch (ex: Exception) {
                    log.error("Error running kafka consumer, unsubscribing and waiting 10 seconds for retry", ex)
                    kafkaConsumer.unsubscribe()
                    delay(10_000)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun start() = withContext(Dispatchers.Unbounded) {
        val endDateTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(10)
        while (shouldRun) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(10))
            records.forEach { record ->
                when (val sykmelding = record.value()) {
                    null -> {
                        println("Sykmelding er null")
                    }
                    else -> {
                        val sykmeldingKafkaMessage = objectMapper.readValue<SendtSykmeldingKafkaMessage>(sykmelding)
                        if (sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.maxOf { it.tom }
                                .isAfter(LocalDate.now().minusMonths(4))
                        ) {
                            try {
                                sykmeldingService.updateSykmeldt(sykmeldingKafkaMessage.kafkaMetadata.fnr)
                            } catch (e: SyketilfelleNotFoundException) {
                                log.warn("Fant ikke syketilfelle for sykmelding med id: ${sykmeldingKafkaMessage.kafkaMetadata.sykmeldingId}")
                            } catch (e: NameNotFoundInPdlException) {
                                log.warn("Fant ikke navn for sykmeldt med sykmelding_id: ${sykmeldingKafkaMessage.kafkaMetadata.sykmeldingId}")
                            }
                        }
                    }
                }
            }
            if (records.count() > 0) {
                lastTimestamp =
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(records.last().timestamp()), ZoneOffset.UTC)
            }

            if (lastTimestamp > endDateTime) {
                shouldRun = false
            }
        }
        log.info("Done with updating sykmeldt")
    }
}
