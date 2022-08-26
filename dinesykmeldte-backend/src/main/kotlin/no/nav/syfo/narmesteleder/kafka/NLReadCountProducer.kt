package no.nav.syfo.narmesteleder.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.kafka.model.NLReadCountKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class NLReadCountProducer(private val kafkaProducer: KafkaProducer<String, NLReadCountKafkaMessage>, private val topicName: String) {
    suspend fun send(nlReadCountMessage: NLReadCountKafkaMessage) {
        withContext(Dispatchers.IO) {
            try {
                kafkaProducer.send(ProducerRecord(topicName, nlReadCountMessage.nlReadCount.narmestelederId, nlReadCountMessage)).get()
            } catch (ex: Exception) {
                log.error("Noe gikk galt ved skriving av lest status til kafka for narmestelederId {}, {}", nlReadCountMessage.nlReadCount.narmestelederId, ex.message)
                throw ex
            }
        }
    }
}
