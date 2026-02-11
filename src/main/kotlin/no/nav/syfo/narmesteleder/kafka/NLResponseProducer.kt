package no.nav.syfo.narmesteleder.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.narmesteleder.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.util.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class NLResponseProducer(
    private val kafkaProducer: KafkaProducer<String, NlResponseKafkaMessage>,
    private val topicName: String,
) {
    private val log = logger()

    suspend fun send(nlResponseKafkaMessage: NlResponseKafkaMessage) {
        withContext(Dispatchers.IO) {
            try {
                kafkaProducer
                    .send(
                        ProducerRecord(
                            topicName,
                            nlResponseKafkaMessage.nlAvbrutt.orgnummer,
                            nlResponseKafkaMessage,
                        ),
                    ).get()
            } catch (ex: Exception) {
                log.error(
                    "Noe gikk galt ved skriving av avbryting av NL til kafka for orgnummer {}, {}",
                    nlResponseKafkaMessage.nlAvbrutt.orgnummer,
                    ex.message,
                )
                throw ex
            }
        }
    }
}
