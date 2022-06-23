package no.nav.syfo.sykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.Environment
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
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

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        kafkaConsumer.subscribe(listOf(environment.sendtSykmeldingTopic))
        GlobalScope.launch(Dispatchers.Unbounded) {

            val endDateTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
            var shouldRun = true

            var lastTimestamp = OffsetDateTime.MIN
            GlobalScope.launch(Dispatchers.Unbounded) {
                while (shouldRun) {
                    log.info("last timestamp: $lastTimestamp")
                    delay(Duration.ofSeconds(10).toMillis())
                }
                log.info("last timestamp: $lastTimestamp")
            }
            while (shouldRun) {
                val records = kafkaConsumer.poll(Duration.ofSeconds(1000))
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
                                sykmeldingService.updateSykmeldt(sykmeldingKafkaMessage.kafkaMetadata.fnr)
                            }
                        }
                    }
                    lastTimestamp = OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp()), ZoneOffset.UTC)
                    if (record.timestamp() > endDateTime.toInstant().toEpochMilli()) {
                        shouldRun = false
                    }
                }
            }
            log.info("Done with updating sykmeldt")
        }
    }
}
