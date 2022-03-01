package no.nav.syfo.narmesteleder.kafka

import no.nav.syfo.log
import no.nav.syfo.narmesteleder.kafka.model.NlResponseKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class NLResponseProducer(private val kafkaProducer: KafkaProducer<String, NlResponseKafkaMessage>, private val topicName: String) {
    fun send(nlResponseKafkaMessage: NlResponseKafkaMessage) {
        try {
            kafkaProducer.send(ProducerRecord(topicName, nlResponseKafkaMessage.nlAvbrutt.orgnummer, nlResponseKafkaMessage)).get()
        } catch (ex: Exception) {
            log.error("Noe gikk galt ved skriving av avbryting av NL til kafka for orgnummer {}, {}", nlResponseKafkaMessage.nlAvbrutt.orgnummer, ex.message)
            throw ex
        }
    }
}
