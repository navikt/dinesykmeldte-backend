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

    private suspend fun start() {
        var processedMessages = 0
        while (applicationState.ready) {
            val soknader = kafkaConsumer.poll(Duration.ZERO)
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
            delay(1)
        }
    }

    fun handleHendelse(dineSykmeldteHendelse: DineSykmeldteHendelse) {
        if (dineSykmeldteHendelse.opprettHendelse != null) {
            hendelserDb.insertHendelse(opprettHendelseTilHendelseDbModel(dineSykmeldteHendelse.opprettHendelse))
        } else if (dineSykmeldteHendelse.ferdigstillHendelse != null) {
            hendelserDb.ferdigstillHendelse(dineSykmeldteHendelse.ferdigstillHendelse.id, dineSykmeldteHendelse.ferdigstillHendelse.oppgavetype, dineSykmeldteHendelse.ferdigstillHendelse.timestamp)
        } else {
            log.error("Har mottatt hendelse som ikke er oppretting eller ferdigstilling for id ${dineSykmeldteHendelse.id}")
            throw IllegalStateException("Mottatt hendelse er ikke oppretting eller ferdigstilling")
        }
    }

    private fun opprettHendelseTilHendelseDbModel(opprettHendelse: OpprettHendelse): HendelseDbModel {
        if (opprettHendelse.orgnummer == null || opprettHendelse.ansattFnr == null) {
            when (opprettHendelse.oppgavetype) {
                OPPGAVETYPE_LES_SYKMELDING -> {
                    val sykmelding = hendelserDb.getSykmelding(opprettHendelse.id)
                    if (sykmelding == null) {
                        log.error("Fant ikke sykmelding som hører til hendelse med id ${opprettHendelse.id}")
                        throw IllegalStateException("Fant ikke sykmelding som hører til mottatt hendelse")
                    }
                    return HendelseDbModel(
                        id = opprettHendelse.id,
                        pasientFnr = sykmelding.pasientFnr,
                        orgnummer = sykmelding.orgnummer,
                        oppgavetype = opprettHendelse.oppgavetype,
                        lenke = opprettHendelse.lenke,
                        tekst = opprettHendelse.tekst,
                        timestamp = opprettHendelse.timestamp,
                        utlopstidspunkt = opprettHendelse.utlopstidspunkt,
                        ferdigstilt = false,
                        ferdigstiltTimestamp = null
                    )
                }
                OPPGAVETYPE_LES_SOKNAD -> {
                    val soknad = hendelserDb.getSoknad(opprettHendelse.id)
                    if (soknad == null) {
                        log.error("Fant ikke søknad som hører til hendelse med id ${opprettHendelse.id}")
                        throw IllegalStateException("Fant ikke søknad som hører til mottatt hendelse")
                    }
                    return HendelseDbModel(
                        id = opprettHendelse.id,
                        pasientFnr = soknad.pasientFnr,
                        orgnummer = soknad.orgnummer,
                        oppgavetype = opprettHendelse.oppgavetype,
                        lenke = opprettHendelse.lenke,
                        tekst = opprettHendelse.tekst,
                        timestamp = opprettHendelse.timestamp,
                        utlopstidspunkt = opprettHendelse.utlopstidspunkt,
                        ferdigstilt = false,
                        ferdigstiltTimestamp = null
                    )
                }
                else -> {
                    log.error("Fnr og/eller orgnummer mangler for hendelse med id ${opprettHendelse.id}")
                    throw IllegalStateException("Fnr og/eller orgnummer mangler for mottatt hendelse")
                }
            }
        } else {
            return HendelseDbModel(
                id = opprettHendelse.id,
                pasientFnr = opprettHendelse.ansattFnr,
                orgnummer = opprettHendelse.orgnummer,
                oppgavetype = opprettHendelse.id,
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
