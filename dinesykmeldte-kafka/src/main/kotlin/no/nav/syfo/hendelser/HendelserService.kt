package no.nav.syfo.hendelser

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.metrics.HENDELSE_TOPIC_COUNTER
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.hendelser.kafka.model.DineSykmeldteHendelse
import no.nav.syfo.hendelser.kafka.model.OpprettHendelse
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord

class HendelserService(
    private val hendelserDb: HendelserDb
) {
    suspend fun handleHendelse(record: ConsumerRecord<String, String>) {
        try {
            handleHendelse(objectMapper.readValue<DineSykmeldteHendelse>(record.value()))
        } catch (e: Exception) {
            log.error("Noe gikk galt ved mottak av hendelse med id ${record.key()}")
            throw e
        }
    }

    fun handleHendelse(dineSykmeldteHendelse: DineSykmeldteHendelse) {
        if (dineSykmeldteHendelse.opprettHendelse != null) {
            hendelserDb.insertHendelse(opprettHendelseTilHendelseDbModel(dineSykmeldteHendelse.id, dineSykmeldteHendelse.opprettHendelse))
            HENDELSE_TOPIC_COUNTER.labels("opprett").inc()
        } else if (dineSykmeldteHendelse.ferdigstillHendelse != null) {
            hendelserDb.ferdigstillHendelse(dineSykmeldteHendelse.id, dineSykmeldteHendelse.ferdigstillHendelse.timestamp)
            HENDELSE_TOPIC_COUNTER.labels("ferdigstill").inc()
        } else {
            log.error("Har mottatt hendelse som ikke er oppretting eller ferdigstilling for id ${dineSykmeldteHendelse.id}")
            throw IllegalStateException("Mottatt hendelse er ikke oppretting eller ferdigstilling")
        }
    }

    private fun opprettHendelseTilHendelseDbModel(hendelseId: String, opprettHendelse: OpprettHendelse): HendelseDbModel {
        return HendelseDbModel(
            id = hendelseId,
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
