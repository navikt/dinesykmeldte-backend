package no.nav.syfo.readcount.kafka.model

import java.time.OffsetDateTime

class KafkaMetadata(val timestamp: OffsetDateTime, val source: String)
