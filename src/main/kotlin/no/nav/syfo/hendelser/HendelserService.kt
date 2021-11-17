package no.nav.syfo.hendelser

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.hendelser.kafka.model.DineSykmeldteHendelse
import no.nav.syfo.hendelser.kafka.model.OpprettHendelse
import no.nav.syfo.log
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.Instant

class HendelserService(
    private val kafkaConsumer: KafkaConsumer<String, DineSykmeldteHendelse>,
    private val hendelserDb: HendelserDb,
    private val applicationState: ApplicationState,
    private val hendelserTopic: String
) {
    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L

    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    log.info("Starting consuming topic $hendelserTopic")
                    kafkaConsumer.subscribe(listOf(hendelserTopic))
                    start()
                } catch (ex: Exception) {
                    log.error("Error running kafka consumer for hendelser, unsubscribing and waiting 10 seconds for retry", ex)
                    kafkaConsumer.unsubscribe()
                    delay(10_000)
                }
            }
        }
    }

    private fun start() {
        var processedMessages = 0
        while (applicationState.ready) {
            val soknader = kafkaConsumer.poll(Duration.ofSeconds(10))
            soknader.forEach {
                try {
                    handleHendelse(it.value())
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved mottak av hendelse med id ${it.key()}")
                    throw e
                }
            }
            kafkaConsumer.commitSync()
            processedMessages += soknader.count()
            processedMessages = logProcessedMessages(processedMessages)
        }
    }

    fun handleHendelse(dineSykmeldteHendelse: DineSykmeldteHendelse) {
        if (dineSykmeldteHendelse.opprettHendelse != null) {
            if (!(dineSykmeldteHendelse.opprettHendelse.oppgavetype == OPPGAVETYPE_LES_SYKMELDING || dineSykmeldteHendelse.opprettHendelse.oppgavetype == OPPGAVETYPE_LES_SOKNAD)) {
                hendelserDb.insertHendelse(opprettHendelseTilHendelseDbModel(dineSykmeldteHendelse.opprettHendelse))
            } else {
                log.info("Oppretter ikke hendelse for sykmelding/søknad med id ${dineSykmeldteHendelse.id}")
            }
        } else if (dineSykmeldteHendelse.ferdigstillHendelse != null) {
            hendelserDb.ferdigstillHendelse(dineSykmeldteHendelse.ferdigstillHendelse.id, dineSykmeldteHendelse.ferdigstillHendelse.oppgavetype, dineSykmeldteHendelse.ferdigstillHendelse.timestamp)
        } else {
            log.error("Har mottatt hendelse som ikke er oppretting eller ferdigstilling for id ${dineSykmeldteHendelse.id}")
            throw IllegalStateException("Mottatt hendelse er ikke oppretting eller ferdigstilling")
        }
    }

    private fun opprettHendelseTilHendelseDbModel(opprettHendelse: OpprettHendelse): HendelseDbModel {
        if (opprettHendelse.orgnummer == null || opprettHendelse.ansattFnr == null) {
            log.error("Fnr og/eller orgnummer mangler for hendelse med id ${opprettHendelse.id}")
            throw IllegalStateException("Fnr og/eller orgnummer mangler for mottatt hendelse")
        } else {
            return HendelseDbModel(
                id = opprettHendelse.id,
                pasientFnr = opprettHendelse.ansattFnr,
                orgnummer = opprettHendelse.orgnummer,
                oppgavetype = opprettHendelse.oppgavetype,
                lenke = opprettHendelse.lenke,
                tekst = opprettHendelse.tekst,
                timestamp = opprettHendelse.timestamp,
                utlopstidspunkt = opprettHendelse.utlopstidspunkt,
                ferdigstilt = false,
                ferdigstiltTimestamp = null
            )
        }
    }

    private fun logProcessedMessages(processedMessages: Int): Int {
        var currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            log.info("Processed $processedMessages hendelser")
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }
}
