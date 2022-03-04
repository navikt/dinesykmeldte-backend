package no.nav.syfo.sykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.metrics.SYKMELDING_TOPIC_COUNTER
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.syketilfelle.client.SyfoSyketilfelleClient
import no.nav.syfo.syketilfelle.client.SyketilfelleNotFoundException
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.mapper.SykmeldingMapper.Companion.toSykmeldingDbModel
import no.nav.syfo.sykmelding.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.sykmelding.pdl.model.formatName
import no.nav.syfo.sykmelding.pdl.service.PdlPersonService
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.LocalDate

class SykmeldingService(
    private val sykmeldingDb: SykmeldingDb,
    private val pdlPersonService: PdlPersonService,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val cluster: String
) {
    suspend fun handleSendtSykmelding(record: ConsumerRecord<String, String?>) {
        try {
            handleSendtSykmelding(record.key(), record.value()?.let { objectMapper.readValue<SendtSykmeldingKafkaMessage>(it) })
        } catch (ex: NameNotFoundInPdlException) {
            if (cluster != "dev-gcp") {
                throw ex
            } else {
                log.info("Ignoring sykmelding when person is not found in pdl for sykmelding: ${record.key()}")
            }
        } catch (ex: SyketilfelleNotFoundException) {
            if (cluster != "dev-gcp") {
                throw ex
            } else {
                log.info("Ignoring sykmelding when syketilfelle is not found in syfosyketilfelle for sykmelding: ${record.key()}")
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved mottak av sendt sykmelding med id ${record.key()}")
            throw e
        }
    }

    suspend fun handleSendtSykmelding(sykmeldingId: String, sykmelding: SendtSykmeldingKafkaMessage?) {
        if (sykmelding == null) {
            log.info("Sletter sykmelding med id $sykmeldingId")
            sykmeldingDb.remove(sykmeldingId)
        } else {
            val sisteTom = finnSisteTom(sykmelding.sykmelding.sykmeldingsperioder)
            if (sisteTom.isAfter(LocalDate.now().minusMonths(4))) {
                if (sykmelding.event.arbeidsgiver == null) {
                    throw IllegalStateException("Mottatt sendt sykmelding uten arbeidsgiver, $sykmeldingId")
                }
                val person = pdlPersonService.getPerson(fnr = sykmelding.kafkaMetadata.fnr, callId = sykmeldingId)
                val startdato = syfoSyketilfelleClient.finnStartdato(fnr = sykmelding.kafkaMetadata.fnr, sykmeldingId = sykmeldingId)
                sykmeldingDb.insertOrUpdate(
                    toSykmeldingDbModel(sykmelding, sisteTom),
                    SykmeldtDbModel(
                        pasientFnr = sykmelding.kafkaMetadata.fnr,
                        pasientNavn = person.navn.formatName(),
                        startdatoSykefravaer = startdato,
                        latestTom = sisteTom
                    )
                )
            }
        }
        SYKMELDING_TOPIC_COUNTER.inc()
    }

    private fun finnSisteTom(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}
