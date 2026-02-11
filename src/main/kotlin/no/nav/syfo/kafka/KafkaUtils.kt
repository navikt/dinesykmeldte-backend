package no.nav.syfo.kafka

import no.nav.syfo.isLocalEnv
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.*
import java.util.Properties
import kotlin.reflect.KClass

class KafkaUtils {
    companion object {
        fun getKafkaConfig(clientId: String): Properties =
            Properties().also {
                val kafkaEnv = if (isLocalEnv()) KafkaEnvironment.createLocal() else KafkaEnvironment()
                it[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = kafkaEnv.KAFKA_BROKERS
                it[CommonClientConfigs.CLIENT_ID_CONFIG] = "${kafkaEnv.KAFKA_CLIENT_ID}-$clientId"
                if (kafkaEnv.SSL) {
                    it[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
                    it[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "jks"
                    it[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
                    it[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = kafkaEnv.KAFKA_TRUSTSTORE_PATH
                    it[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] =
                        kafkaEnv.KAFKA_CREDSTORE_PASSWORD
                    it[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = kafkaEnv.KAFKA_KEYSTORE_PATH
                    it[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = kafkaEnv.KAFKA_CREDSTORE_PASSWORD
                    it[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = kafkaEnv.KAFKA_CREDSTORE_PASSWORD
                    it[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
                }
                it[ProducerConfig.ACKS_CONFIG] = "all"
                it[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
            }
    }
}

fun <T> createKafkaProducer(clientId: String): KafkaProducer<String, T> =
    KafkaProducer(
        KafkaUtils
            .getKafkaConfig(clientId)
            .toProducerConfig(
                JacksonKafkaSerializer::class,
                StringSerializer::class,
            ),
    )

fun Properties.toConsumerConfig(
    groupId: String,
    valueDeserializer: KClass<out Deserializer<out Any>>,
    keyDeserializer: KClass<out Deserializer<out Any>> = StringDeserializer::class,
): Properties =
    Properties().also {
        it.putAll(this)
        it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = keyDeserializer.java
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = valueDeserializer.java
        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
    }

fun Properties.toProducerConfig(
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = StringSerializer::class,
): Properties =
    Properties().also {
        it.putAll(this)
        it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = valueSerializer.java
        it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = keySerializer.java
    }
