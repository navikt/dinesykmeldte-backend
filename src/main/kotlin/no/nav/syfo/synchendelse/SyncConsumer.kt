package no.nav.syfo.synchendelse

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import no.nav.syfo.util.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer

class SyncConsumer(
    private val syncHendelseDb: SyncHendelseDb,
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val topic: String,
) {

    suspend fun start() = coroutineScope {
        kafkaConsumer.subscribe(listOf(topic))
        while (isActive) {
            kafkaConsumer
                .poll(Duration.ofSeconds(10))
                .map { objectMapper.readValue<SyncHendelse>(it.value()) }
                .filter { it.source != SyncSource.TSM }
                .forEach {
                    when (it.type) {
                        SyncHendelseType.SYKMELDING -> syncHendelseDb.markSykmeldingerRead(it.id)
                        SyncHendelseType.SOKNAD -> syncHendelseDb.markSoknadRead(it.id)
                        SyncHendelseType.HENDELSE -> syncHendelseDb.markHendelserRead(it.id)
                    }
                }
        }
        kafkaConsumer.unsubscribe()
    }
}
