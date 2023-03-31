package no.nav.syfo.narmesteleder

import no.nav.syfo.log
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.kafka.model.NlAvbrutt
import no.nav.syfo.narmesteleder.kafka.model.NlResponseKafkaMessage
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class NarmestelederService(
    private val narmestelederDb: NarmestelederDb,
    private val nlResponseProducer: NLResponseProducer,
) {

    suspend fun deaktiverNarmesteLeder(fnrLeder: String, narmestelederId: String, callId: UUID) {
        val nlKoblinger = narmestelederDb.finnNarmestelederkoblinger(
            narmesteLederFnr = fnrLeder,
            narmestelederId = narmestelederId,
        )
        if (nlKoblinger.isNotEmpty()) {
            log.info("Deaktiverer ${nlKoblinger.size} NL-koblinger for $callId")
            deaktiverNarmesteLeder(
                orgnummer = nlKoblinger.first().orgnummer,
                fnrSykmeldt = nlKoblinger.first().pasientFnr,
            )
            nlKoblinger.forEach { narmestelederDb.remove(it.narmestelederId) }
        } else {
            log.info("Ingen aktive koblinger å deaktivere $callId")
        }
    }

    private suspend fun deaktiverNarmesteLeder(orgnummer: String, fnrSykmeldt: String) {
        nlResponseProducer.send(
            NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    source = "leder",
                ),
                nlAvbrutt = NlAvbrutt(
                    orgnummer = orgnummer,
                    sykmeldtFnr = fnrSykmeldt,
                    aktivTom = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            ),
        )
    }
}
