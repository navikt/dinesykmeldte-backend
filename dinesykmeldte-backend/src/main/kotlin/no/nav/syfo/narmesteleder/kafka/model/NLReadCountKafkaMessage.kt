package no.nav.syfo.narmesteleder.kafka.model

data class NLReadCountKafkaMessage(
    val kafkaMetadata: KafkaMetadata,
    val nlReadCount: NLReadCount,
)

data class NLReadCount(
    val narmestelederId: String,
    val unreadSykmeldinger: Int,
    val unreadSoknader: Int,
    val unreadDialogmoter: Int,
    val unreadOppfolgingsplaner: Int,
    val unreadMeldinger: Int,
)
