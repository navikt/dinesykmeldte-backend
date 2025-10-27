package no.nav.syfo.sykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDate
import no.nav.syfo.application.metrics.SYKMELDING_TOPIC_COUNTER
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.model.formatName
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.syketilfelle.client.SyfoSyketilfelleClient
import no.nav.syfo.syketilfelle.client.SyketilfelleNotFoundException
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldingInfo
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.model.SykmeldingMapper.Companion.toSykmeldingDbModel
import no.nav.syfo.sykmelding.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.util.logger
import no.nav.syfo.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord

class SykmeldingService(
    private val sykmeldingDb: SykmeldingDb,
    private val pdlPersonService: PdlPersonService,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val cluster: String,
) {
    private val log = logger()

    suspend fun handleSendtSykmeldingKafkaMessage(record: ConsumerRecord<String, String?>) {
        try {
            handleSendtSykmeldingKafkaMessage(
                record.key(),
                record.value()?.let { objectMapper.readValue<SendtSykmeldingKafkaMessage>(it) },
            )
        } catch (ex: NameNotFoundInPdlException) {
            if (cluster != "dev-gcp") {
                throw ex
            } else {
                log.info(
                    "Ignoring sykmelding when person is not found in pdl for sykmelding: ${record.key()}",
                )
            }
        } catch (ex: SyketilfelleNotFoundException) {
            if (cluster != "dev-gcp") {
                throw ex
            } else {
                log.info(
                    "Ignoring sykmelding when syketilfelle is not found in syfosyketilfelle for sykmelding: ${record.key()}",
                )
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved mottak av sendt sykmelding med id ${record.key()}")
            throw e
        }
    }

    suspend fun handleSendtSykmeldingKafkaMessage(
        sykmeldingId: String,
        sykmelding: SendtSykmeldingKafkaMessage?
    ) {
        val existingSykmelding = sykmeldingDb.getSykmeldingInfo(sykmeldingId)
        when (sykmelding) {
            null -> deleteSykmelding(sykmeldingId, existingSykmelding)
            else -> handleSendtSykmelding(sykmelding, sykmeldingId, existingSykmelding)
        }
        SYKMELDING_TOPIC_COUNTER.inc()
    }

    private suspend fun handleSendtSykmelding(
        sykmelding: SendtSykmeldingKafkaMessage,
        sykmeldingId: String,
        existingSykmelding: SykmeldingInfo?,
    ) {
        val sisteTom = finnSisteTom(sykmelding.sykmelding.sykmeldingsperioder)
        if (sisteTom.isAfter(LocalDate.now().minusMonths(4))) {
            if (sykmelding.event.arbeidsgiver == null) {
                throw IllegalStateException(
                    "Mottatt sendt sykmelding uten arbeidsgiver, $sykmeldingId",
                )
            }
            sykmeldingDb.insertOrUpdateSykmelding(toSykmeldingDbModel(sykmelding, sisteTom))
            updateSykmeldt(sykmelding.kafkaMetadata.fnr)
        } else if (existingSykmelding != null) {
            deleteSykmelding(existingSykmelding.sykmeldingId, existingSykmelding)
        }
    }

    private suspend fun deleteSykmelding(
        sykmeldingId: String,
        existingSykmelding: SykmeldingInfo?
    ) {
        if (existingSykmelding != null) {
            log.info("Sletter sykmelding med id $sykmeldingId")
            sykmeldingDb.remove(sykmeldingId)
            updateSykmeldt(existingSykmelding.fnr)
        }
    }

    suspend fun updateSykmeldt(fnr: String) {
        val sykmeldingInfos = sykmeldingDb.getSykmeldingInfos(fnr)

        when (val latestSykmelding = sykmeldingInfos.maxByOrNull { it.latestTom }) {
            null -> sykmeldingDb.deleteSykmeldt(fnr)
            else -> {
                val person =
                    pdlPersonService.getPerson(fnr = fnr, callId = latestSykmelding.sykmeldingId)

                val startdato =
                    syfoSyketilfelleClient.finnStartdato(
                        fnr = fnr,
                        sykmeldingId = latestSykmelding.sykmeldingId,
                    )

                sykmeldingDb.insertOrUpdateSykmeldt(
                    SykmeldtDbModel(
                        pasientFnr = fnr,
                        pasientNavn = person.navn.formatName(),
                        startdatoSykefravaer = startdato,
                        latestTom = latestSykmelding.latestTom,
                        sistOppdatert = LocalDate.now(),
                    ),
                )
            }
        }
    }

    fun getActiveSendtSykmeldingsperioder(fnr: String, orgnummer: String): Boolean {
        val antallSykmeldinger =
            sykmeldingDb.getActiveSendtSykmeldingsperioder(fnr, orgnummer)?.firstOrNull()

        return antallSykmeldinger != null && antallSykmeldinger > 0
    }

    private fun finnSisteTom(perioder: List<SykmeldingsperiodeAGDTO>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}
