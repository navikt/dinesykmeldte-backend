package no.nav.syfo.narmesteleder

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.metrics.NL_TOPIC_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.narmesteleder.kafka.model.NlAvbrutt
import no.nav.syfo.narmesteleder.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class NarmestelederService(
    private val narmestelederDb: NarmestelederDb,
    private val nlResponseProducer: NLResponseProducer
) {
    fun updateNl(record: ConsumerRecord<String, String>) {
        try {
            updateNl(objectMapper.readValue<NarmestelederLeesahKafkaMessage>(record.value()))
        } catch (e: Exception) {
            log.error("Noe gikk galt ved mottak av oppdatert nærmeste leder med id ${record.key()}")
            throw e
        }
    }

    fun updateNl(narmesteleder: NarmestelederLeesahKafkaMessage) {
        when (narmesteleder.aktivTom) {
            null -> {
                narmestelederDb.insertOrUpdate(narmesteleder)
                NL_TOPIC_COUNTER.labels("ny").inc()
            }
            else -> {
                narmestelederDb.remove(narmesteleder.narmesteLederId.toString())
                NL_TOPIC_COUNTER.labels("avbrutt").inc()
            }
        }
    }

    fun deaktiverNarmesteLeder(fnrLeder: String, orgnummer: String, fnrSykmeldt: String, callId: UUID) {
        val nlKoblinger = narmestelederDb.finnNarmestelederkoblinger(
            narmesteLederFnr = fnrLeder,
            orgnummer = orgnummer,
            fnrSykmeldt = fnrSykmeldt
        )
        if (nlKoblinger.isNotEmpty()) {
            log.info("Deaktiverer ${nlKoblinger.size} NL-koblinger for $callId")
            deaktiverNarmesteLeder(orgnummer = nlKoblinger.first().orgnummer, fnrSykmeldt = nlKoblinger.first().pasientFnr)
            nlKoblinger.forEach { narmestelederDb.remove(it.narmestelederId) }
        } else {
            log.info("Ingen aktive koblinger å deaktivere $callId")
        }
    }

    private fun deaktiverNarmesteLeder(orgnummer: String, fnrSykmeldt: String) {
        nlResponseProducer.send(
            NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    source = "leder"
                ),
                nlAvbrutt = NlAvbrutt(
                    orgnummer = orgnummer,
                    sykmeldtFnr = fnrSykmeldt,
                    aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )
        )
    }
}
