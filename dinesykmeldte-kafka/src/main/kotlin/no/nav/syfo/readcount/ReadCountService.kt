package no.nav.syfo.readcount

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import no.nav.syfo.log
import no.nav.syfo.readcount.db.HendelseDbModel
import no.nav.syfo.readcount.db.MinSykmeldtDbModel
import no.nav.syfo.readcount.db.ReadCountDb
import no.nav.syfo.readcount.kafka.NLReadCountProducer
import no.nav.syfo.readcount.kafka.model.KafkaMetadata
import no.nav.syfo.readcount.kafka.model.NLReadCount
import no.nav.syfo.readcount.kafka.model.NLReadCountKafkaMessage
import no.nav.syfo.readcount.model.DialogmoteHendelser
import no.nav.syfo.readcount.model.Hendelse
import no.nav.syfo.readcount.model.HendelseType
import no.nav.syfo.readcount.model.MinSykmeldtKey
import no.nav.syfo.readcount.model.OppfolgingsplanerHendelser
import no.nav.syfo.readcount.model.PreviewFremtidigSoknad
import no.nav.syfo.readcount.model.PreviewNySoknad
import no.nav.syfo.readcount.model.PreviewSendtSoknad
import no.nav.syfo.readcount.model.PreviewSoknad
import no.nav.syfo.readcount.model.PreviewSoknadMapper.Companion.toPreviewSoknad
import no.nav.syfo.readcount.model.PreviewSykmeldt
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ReadCountService(
    private val readCountDb: ReadCountDb,
    private val nlReadCountProducer: NLReadCountProducer
) {
    suspend fun updateReadCountKafkaTopic(pasientFnr: String, orgnummer: String) {
        val narmesteleder = readCountDb.getNarmesteleder(
            pasientFnr = pasientFnr,
            orgnummer = orgnummer
        )

        if (narmesteleder != null) {
            val sykmeldt = getMineSykmeldte(narmesteleder.lederFnr).find {
                it.narmestelederId == narmesteleder.narmestelederId
            }
            if (sykmeldt != null) {
                nlReadCountProducer.send(
                    NLReadCountKafkaMessage(
                        KafkaMetadata(
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                            source = "dinesykmeldte-kafka"
                        ),
                        NLReadCount(
                            narmestelederId = sykmeldt.narmestelederId,
                            unreadSykmeldinger = sykmeldt.antallUlesteSykmeldinger,
                            unreadSoknader = sykmeldt.antallUlesteSoknader,
                            unreadMeldinger = sykmeldt.antallAktivitetsvarsler,
                            unreadDialogmoter = sykmeldt.antallDialogmoter,
                            unreadOppfolgingsplaner = sykmeldt.antallOppfolgingsplaner,
                        )
                    )
                )
            } else {
                log.warn("Fant ikke sykmeldt for narmestelederId ${narmesteleder.narmestelederId} ved oppdatering av leststatus")
            }
        }
    }

    private suspend fun getMineSykmeldte(lederFnr: String): List<PreviewSykmeldt> = withContext(Dispatchers.IO) {
        val hendelserJob = async(Dispatchers.IO) { readCountDb.getHendelser(lederFnr) }
        val sykmeldteMapJob =
            async(Dispatchers.IO) { readCountDb.getMineSykmeldte(lederFnr).groupBy { it.toMinSykmeldtKey() } }

        val hendelserMap = hendelserJob.await().groupBy { it.pasientFnr }
            .mapValues { it.value.map { hendelse -> hendelse.toHendelse() } }

        val sykmeldteMap = sykmeldteMapJob.await()

        return@withContext sykmeldteMap.map { sykmeldtEntry ->
            PreviewSykmeldt(
                narmestelederId = sykmeldtEntry.key.narmestelederId,
                antallUlesteSykmeldinger = getAntallSykmeldinger(sykmeldtEntry),
                antallUlesteSoknader = getAntallUlesteSoknader(sykmeldtEntry, hendelserMap),
                antallDialogmoter = getAntallDialogmoter(hendelserMap, sykmeldtEntry),
                antallAktivitetsvarsler = getAntallAktivitetsvarsler(hendelserMap, sykmeldtEntry),
                antallOppfolgingsplaner = getAntallOppfolgingsplaner(hendelserMap, sykmeldtEntry)
            )
        }
    }

    private fun MinSykmeldtDbModel.toMinSykmeldtKey(): MinSykmeldtKey = MinSykmeldtKey(
        narmestelederId = this.narmestelederId,
        orgnummer = this.orgnummer,
        fnr = this.sykmeldtFnr
    )

    private fun isSoknadUnread(soknad: PreviewSoknad): Boolean =
        when (soknad) {
            is PreviewSendtSoknad -> !soknad.lest
            is PreviewNySoknad -> if (soknad.ikkeSendtSoknadVarsel) !soknad.lest else false
            is PreviewFremtidigSoknad -> false
        }

    private fun HendelseDbModel.toHendelse() =
        Hendelse(
            id = id,
            oppgavetype = safeParseHendelseEnum(oppgavetype),
            ferdigstilt = ferdigstiltTimestamp
        )

    private fun safeParseHendelseEnum(oppgavetype: String): HendelseType {
        return try {
            HendelseType.valueOf(oppgavetype)
        } catch (e: Exception) {
            log.error("Ukjent oppgave av type $oppgavetype er ikke håndtert i applikasjonen. Mangler vi implementasjon?")
            HendelseType.UNKNOWN
        }
    }

    private fun getAntallSykmeldinger(sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>) =
        sykmeldtEntry.value.distinctBy { it.sykmeldingId }
            .filter { !it.lestSykmelding }.size

    private fun getAntallDialogmoter(
        hendelserMap: Map<String, List<Hendelse>>,
        sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>,
    ) = hendelserMap[sykmeldtEntry.key.fnr]
        ?.filter { ma -> DialogmoteHendelser.contains(ma.oppgavetype) }
        ?.filter { it.ferdigstilt == null }?.size
        ?: 0

    private fun getAntallAktivitetsvarsler(
        hendelserMap: Map<String, List<Hendelse>>,
        sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>,
    ): Int = (
        hendelserMap[sykmeldtEntry.key.fnr]
            ?.filter { ma -> ma.oppgavetype == HendelseType.AKTIVITETSKRAV }
            ?.filter { it.ferdigstilt == null }?.size
            ?: 0
        )

    private fun getAntallOppfolgingsplaner(
        hendelserMap: Map<String, List<Hendelse>>,
        sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>,
    ) = hendelserMap[sykmeldtEntry.key.fnr]
        ?.filter { OppfolgingsplanerHendelser.contains(it.oppgavetype) }
        ?.filter { it.ferdigstilt == null }?.size
        ?: 0

    private fun getAntallUlesteSoknader(
        sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>,
        hendelserMap: Map<String, List<Hendelse>>,
    ): Int {
        val korrigerteSoknader = sykmeldtEntry.value
            .mapNotNull { it.soknad }
            .mapNotNull { it.korrigerer }

        return sykmeldtEntry.value
            .filter { it.soknad != null && !korrigerteSoknader.contains(it.soknad.id) }
            .mapNotNull { sykmeldt ->
                mapNullableSoknad(sykmeldt, getHendelserForSoknad(hendelserMap, sykmeldtEntry, sykmeldt))
            }.filter { isSoknadUnread(it) }.size
    }

    private fun getHendelserForSoknad(
        hendelserMap: Map<String, List<Hendelse>>,
        sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>,
        sykmeldt: MinSykmeldtDbModel,
    ) = hendelserMap[sykmeldtEntry.key.fnr]?.filter { it.id == sykmeldt.soknad?.id }.orEmpty()

    private fun mapNullableSoknad(
        sykmeldtDbModel: MinSykmeldtDbModel,
        hendelser: List<Hendelse>,
    ): PreviewSoknad? =
        sykmeldtDbModel.soknad?.let {
            toPreviewSoknad(it, sykmeldtDbModel.lestSoknad, hendelser)
        }
}
