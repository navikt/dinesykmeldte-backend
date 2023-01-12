package no.nav.syfo.sistoppdatert

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.metrics.SENDT_TIL_AG_UPDATE_COUNTER
import no.nav.syfo.application.metrics.SIST_OPPDATERT_UPDATE_COUNTER
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.LocalDate

class SistOppdatertService(
    private val sistOppdatertDb: SistOppdatertDb
) {
    fun handleSendtSykmeldingKafkaMessage(record: ConsumerRecord<String, String?>) {
        try {
            record.value()?.let {
                oppdaterSykmeldtOgSykmelding(
                    record.key(),
                    objectMapper.readValue<SendtSykmeldingKafkaMessage>(it)
                )
            }
        } catch (e: Exception) {
            log.error("SistOppdatertService: Noe gikk galt ved mottak av sendt sykmelding med id ${record.key()}")
            throw e
        }
    }

    fun oppdaterSykmeldtOgSykmelding(sykmeldingId: String, sykmelding: SendtSykmeldingKafkaMessage) {
        val sykmeldingFraDb = sistOppdatertDb.getSykmelding(sykmeldingId)
        var fnr = sykmelding.kafkaMetadata.fnr

        if (sykmeldingFraDb != null) {
            if (sykmeldingFraDb.pasientFnr != sykmelding.kafkaMetadata.fnr) {
                fnr = sykmeldingFraDb.pasientFnr
            }
            if (sykmeldingFraDb.sendtTilArbeidsgiverDato == null) {
                sistOppdatertDb.updateSendtTilArbeidsgiverDato(sykmeldingId, sykmelding.event.timestamp)
                SENDT_TIL_AG_UPDATE_COUNTER.inc()
            }
        }

        val sykmeldt = sistOppdatertDb.getSykmeldt(fnr)
        if (sykmeldt != null && sykmeldt.sistOppdatert == null) {
            val sistOppdatert = getSistOppdatert(fnr)
            sistOppdatert?.let {
                sistOppdatertDb.updateSistOppdatert(fnr, sistOppdatert)
                SIST_OPPDATERT_UPDATE_COUNTER.inc()
            }
        }
    }

    private fun getSistOppdatert(fnr: String): LocalDate? {
        val sistOppdatert = sistOppdatertDb.getSistOppdatert(fnr)
        return listOfNotNull(
            sistOppdatert.sisteTimestampSykmelding,
            sistOppdatert.sisteTimestampSoknad,
            sistOppdatert.sisteTimestampHendelse
        ).maxOrNull()?.toLocalDate()
    }
}
