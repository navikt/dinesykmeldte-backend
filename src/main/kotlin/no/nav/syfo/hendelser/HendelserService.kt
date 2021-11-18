package no.nav.syfo.hendelser

import com.fasterxml.jackson.module.kotlin.readValue
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
    fun handleHendelse(record: ConsumerRecord<String, String>) {
        try {
            handleHendelse(objectMapper.readValue<DineSykmeldteHendelse>(record.value()))
        } catch (e: Exception) {
            log.error("Noe gikk galt ved mottak av hendelse med id ${record.key()}")
            throw e
        }
    }

    fun handleHendelse(dineSykmeldteHendelse: DineSykmeldteHendelse) {
        if (dineSykmeldteHendelse.opprettHendelse != null) {
            if (!(dineSykmeldteHendelse.opprettHendelse.oppgavetype == OPPGAVETYPE_LES_SYKMELDING || dineSykmeldteHendelse.opprettHendelse.oppgavetype == OPPGAVETYPE_LES_SOKNAD)) {
                hendelserDb.insertHendelse(opprettHendelseTilHendelseDbModel(dineSykmeldteHendelse.opprettHendelse))
            } else {
                log.debug("Oppretter ikke hendelse for sykmelding/søknad med id ${dineSykmeldteHendelse.id}")
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
}
