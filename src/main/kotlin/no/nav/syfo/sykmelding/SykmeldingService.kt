package no.nav.syfo.sykmelding

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.SYKMELDING_TOPIC_COUNTER
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.LocalDate

class SykmeldingService(
    private val kafkaConsumer: KafkaConsumer<String, SendtSykmeldingKafkaMessage>,
    private val sykmeldingDb: SykmeldingDb,
    private val applicationState: ApplicationState,
    private val sendtSykmeldingTopic: String
) {
    fun start() {
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

    fun handleSendtSykmelding(sykmeldingId: String, sykmelding: SendtSykmeldingKafkaMessage?) {
        if (sykmelding == null) {
            log.info("Sletter sykmelding med id $sykmeldingId")
            sykmeldingDb.remove(sykmeldingId)
        } else {
            val sisteTom = finnSisteTom(sykmelding.sykmelding.sykmeldingsperioder)
            if (sisteTom.isAfter(LocalDate.now().minusWeeks(4))) {
                // hent navn fra PDL
                // hent startdato fra syfosyketilfelle
                // hente lest fra SS?
                // lagre i db
            }
        }
        SYKMELDING_TOPIC_COUNTER.inc()
    }

    fun finnSisteTom(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}
