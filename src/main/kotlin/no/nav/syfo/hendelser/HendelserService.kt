package no.nav.syfo.hendelser

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.hendelser.kafka.model.DineSykmeldteHendelse
import no.nav.syfo.hendelser.kafka.model.OpprettHendelse
import no.nav.syfo.hendelser.kafka.model.sykmeldingsoknad.LestSykmeldingEllerSoknadHendelse
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
            hendelserDb.insertHendelse(opprettHendelseTilHendelseDbModel(dineSykmeldteHendelse.id, dineSykmeldteHendelse.opprettHendelse))
        } else if (dineSykmeldteHendelse.ferdigstillHendelse != null) {
            hendelserDb.ferdigstillHendelse(dineSykmeldteHendelse.id, dineSykmeldteHendelse.ferdigstillHendelse.timestamp)
        } else {
            log.error("Har mottatt hendelse som ikke er oppretting eller ferdigstilling for id ${dineSykmeldteHendelse.id}")
            throw IllegalStateException("Mottatt hendelse er ikke oppretting eller ferdigstilling")
        }
    }

    // LestSykmeldingEllerSoknad fjernes når lest-status for sykmelding og søknad oppdateres via api
    fun handleLestSykmeldingEllerSoknadHendelse(record: ConsumerRecord<String, String>) {
        try {
            handleLestSykmeldingEllerSoknadHendelse(objectMapper.readValue<LestSykmeldingEllerSoknadHendelse>(record.value()))
        } catch (e: Exception) {
            log.error("Noe gikk galt ved mottak av lest sykmelding/søknad-hendelse med id ${record.key()}")
            throw e
        }
    }

    fun handleLestSykmeldingEllerSoknadHendelse(lestSykmeldingEllerSoknadHendelse: LestSykmeldingEllerSoknadHendelse) {
        if (lestSykmeldingEllerSoknadHendelse.opprettHendelse != null) {
            log.debug("Oppretter ikke hendelse for sykmelding/søknad med id ${lestSykmeldingEllerSoknadHendelse.id}")
        } else if (lestSykmeldingEllerSoknadHendelse.ferdigstillHendelse != null) {
            hendelserDb.ferdigstillLestSykmeldingEllerSoknadHendelse(lestSykmeldingEllerSoknadHendelse.ferdigstillHendelse.id)
        } else {
            log.error("Har mottatt lest sykmelding/søknad-hendelse som ikke er oppretting eller ferdigstilling for id ${lestSykmeldingEllerSoknadHendelse.id}")
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
