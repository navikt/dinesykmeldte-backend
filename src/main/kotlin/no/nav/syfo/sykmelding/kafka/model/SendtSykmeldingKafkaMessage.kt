package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.sykmelding.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.sykmelding.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO

data class SendtSykmeldingKafkaMessage(
    val sykmelding: ArbeidsgiverSykmelding,
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO,
)
