package no.nav.syfo.sykmelding

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.SYKMELDING_TOPIC_COUNTER
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.pdl.model.toFormattedNameString
import no.nav.syfo.sykmelding.pdl.service.PdlPersonService
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingService(
    private val kafkaConsumer: KafkaConsumer<String, SendtSykmeldingKafkaMessage>,
    private val sykmeldingDb: SykmeldingDb,
    private val applicationState: ApplicationState,
    private val sendtSykmeldingTopic: String,
    private val pdlPersonService: PdlPersonService
) {
    suspend fun start() {
        kafkaConsumer.subscribe(listOf(sendtSykmeldingTopic))
        log.info("Starting consuming topic $sendtSykmeldingTopic")
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(1)).forEach {
                try {
                    handleSendtSykmelding(it.key(), it.value())
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved mottak av sendt sykmelding med id ${it.key()}")
                    throw e
                }
            }
        }
    }

    suspend fun handleSendtSykmelding(sykmeldingId: String, sykmelding: SendtSykmeldingKafkaMessage?) {
        if (sykmelding == null) {
            log.info("Sletter sykmelding med id $sykmeldingId")
            sykmeldingDb.remove(sykmeldingId)
        } else {
            val sisteTom = finnSisteTom(sykmelding.sykmelding.sykmeldingsperioder)
            if (sisteTom.isAfter(LocalDate.now().minusWeeks(4))) {
                if (sykmelding.event.arbeidsgiver == null) {
                    throw IllegalStateException("Mottatt sendt sykmelding uten arbeidsgiver, $sykmeldingId")
                }
                val person = pdlPersonService.getPerson(fnr = sykmelding.kafkaMetadata.fnr, callId = sykmeldingId)
                // hent navn fra PDL
                // hent startdato fra syfosyketilfelle
                // hente lest fra SS?
                // lagre i db
                sykmeldingDb.insertOrUpdate(
                    SykmeldingDbModel(
                        sykmeldingId = sykmeldingId,
                        pasientFnr = sykmelding.kafkaMetadata.fnr,
                        orgnummer = sykmelding.event.arbeidsgiver!!.orgnummer,
                        orgnavn = sykmelding.event.arbeidsgiver!!.orgNavn,
                        sykmelding = sykmelding.sykmelding,
                        lest = false, // fra strangler
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        latestTom = sisteTom
                    ),
                    SykmeldtDbModel(
                        pasientFnr = sykmelding.kafkaMetadata.fnr,
                        pasientNavn = person.navn.toFormattedNameString(),
                        startdatoSykefravaer = LocalDate.now(), // fra syfosyketilfelle
                        latestTom = sisteTom
                    )
                )
            }
        }
        SYKMELDING_TOPIC_COUNTER.inc()
    }

    fun finnSisteTom(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}
