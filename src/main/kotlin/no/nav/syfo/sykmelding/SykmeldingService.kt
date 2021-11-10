package no.nav.syfo.sykmelding

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.SYKMELDING_TOPIC_COUNTER
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.sykmelding.client.SyfoSyketilfelleClient
import no.nav.syfo.sykmelding.client.SyketilfelleNotFoundException
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.mapper.SykmeldingMapper.Companion.toSykmeldingDbModel
import no.nav.syfo.sykmelding.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.sykmelding.pdl.model.toFormattedNameString
import no.nav.syfo.sykmelding.pdl.service.PdlPersonService
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class SykmeldingService(
    private val kafkaConsumer: KafkaConsumer<String, SendtSykmeldingKafkaMessage>,
    private val sykmeldingDb: SykmeldingDb,
    private val applicationState: ApplicationState,
    private val sendtSykmeldingTopic: String,
    private val pdlPersonService: PdlPersonService,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val cluster: String
) {
    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L
    private var ignoredSykmeldinger = 0

    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    log.info("Starting consuming topic $sendtSykmeldingTopic")
                    kafkaConsumer.subscribe(listOf(sendtSykmeldingTopic))
                    start()
                } catch (ex: Exception) {
                    log.error("Error running kafka consumer, unsubscribing and waiting 10 seconds for retry", ex)
                    kafkaConsumer.unsubscribe()
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun start() {
        var processedMessages = 0
        while (applicationState.ready) {
            val sykmeldinger = kafkaConsumer.poll(Duration.ZERO)
            sykmeldinger.forEach {
                try {
                    handleSendtSykmelding(it.key(), it.value())
                } catch (ex: NameNotFoundInPdlException) {
                    if (cluster != "dev-gcp") {
                        throw ex
                    } else {
                        log.info("Ignoring sykmelding when person is not found in pdl for sykmelding: ${it.key()}")
                    }
                } catch (ex: SyketilfelleNotFoundException) {
                    if (cluster != "dev-gcp") {
                        throw ex
                    } else {
                        log.info("Ignoring sykmelding when syketilfelle is not found in syfosyketilfelle for sykmelding: ${it.key()}")
                    }
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved mottak av sendt sykmelding med id ${it.key()}")
                    throw e
                }
            }
            kafkaConsumer.commitSync()
            processedMessages += sykmeldinger.count()
            processedMessages = logProcessedMessages(processedMessages)
            delay(1)
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
                val startdato = syfoSyketilfelleClient.finnStartdato(aktorId = person.aktorId!!, sykmeldingId = sykmeldingId)
                sykmeldingDb.insertOrUpdate(
                    toSykmeldingDbModel(sykmelding, sisteTom),
                    SykmeldtDbModel(
                        pasientFnr = sykmelding.kafkaMetadata.fnr,
                        pasientNavn = person.navn.toFormattedNameString(),
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

    private fun logProcessedMessages(processedMessages: Int): Int {
        var currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            log.info("Processed $processedMessages messages, ignored sykmeldinger $ignoredSykmeldinger")
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }
}
