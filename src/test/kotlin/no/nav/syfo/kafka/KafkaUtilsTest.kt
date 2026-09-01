package no.nav.syfo.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class KafkaUtilsTest {
    @Test
    fun `consumer bruker en record per poll og committer eksplisitt`() {
        val config =
            Properties().toConsumerConfig(
                groupId = "test-group",
                valueDeserializer = StringDeserializer::class,
            )

        assertEquals("1", config[ConsumerConfig.MAX_POLL_RECORDS_CONFIG])
        assertEquals("false", config[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG])
    }
}
