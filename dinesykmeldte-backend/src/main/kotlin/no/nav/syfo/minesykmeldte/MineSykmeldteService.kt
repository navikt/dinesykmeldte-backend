package no.nav.syfo.minesykmeldte

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.log
import no.nav.syfo.minesykmeldte.MineSykmeldteMapper.Companion.toPreviewSoknad
import no.nav.syfo.minesykmeldte.MineSykmeldteMapper.Companion.toSoknadsperiode
import no.nav.syfo.minesykmeldte.MineSykmeldteMapper.Companion.toSporsmal
import no.nav.syfo.minesykmeldte.db.MinSykmeldtDbModel
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.model.AktivitetIkkeMulig
import no.nav.syfo.minesykmeldte.model.Arbeidsgiver
import no.nav.syfo.minesykmeldte.model.ArbeidsrelatertArsak
import no.nav.syfo.minesykmeldte.model.ArbeidsrelatertArsakEnum
import no.nav.syfo.minesykmeldte.model.Avventende
import no.nav.syfo.minesykmeldte.model.Behandler
import no.nav.syfo.minesykmeldte.model.Behandlingsdager
import no.nav.syfo.minesykmeldte.model.Dialogmote
import no.nav.syfo.minesykmeldte.model.DialogmoteHendelser
import no.nav.syfo.minesykmeldte.model.Gradert
import no.nav.syfo.minesykmeldte.model.Hendelse
import no.nav.syfo.minesykmeldte.model.HendelseType
import no.nav.syfo.minesykmeldte.model.MinSykmeldtKey
import no.nav.syfo.minesykmeldte.model.Periode
import no.nav.syfo.minesykmeldte.model.PreviewSoknad
import no.nav.syfo.minesykmeldte.model.PreviewSykmeldt
import no.nav.syfo.minesykmeldte.model.Reisetilskudd
import no.nav.syfo.minesykmeldte.model.Soknad
import no.nav.syfo.minesykmeldte.model.Sykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.toFormattedNameString
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class MineSykmeldteService(
    private val mineSykmeldteDb: MineSykmeldteDb,
) {
    suspend fun getMineSykmeldte(lederFnr: String): List<PreviewSykmeldt> = withContext(Dispatchers.IO) {
        val hendelserJob = async(Dispatchers.IO) { mineSykmeldteDb.getHendelser(lederFnr) }
        val sykmeldteMapJob =
            async(Dispatchers.IO) { mineSykmeldteDb.getMineSykmeldte(lederFnr).groupBy { it.toMinSykmeldtKey() } }

        val hendelserMap = hendelserJob.await().groupBy { it.pasientFnr }
            .mapValues { it.value.map { hendelse -> hendelse.toHendelse() } }

        val sykmeldteMap = sykmeldteMapJob.await()

        return@withContext sykmeldteMap.map { sykmeldtEntry ->
            PreviewSykmeldt(
                narmestelederId = sykmeldtEntry.key.narmestelederId,
                orgnummer = sykmeldtEntry.key.orgnummer,
                fnr = sykmeldtEntry.key.fnr,
                navn = sykmeldtEntry.key.navn,
                startdatoSykefravar = sykmeldtEntry.key.startDatoSykefravaer,
                friskmeldt = isFriskmeldt(sykmeldtEntry),
                previewSoknader = getPreviewSoknader(sykmeldtEntry, hendelserMap),
                dialogmoter = getDialogmoter(hendelserMap, sykmeldtEntry),
                sykmeldinger = getSykmeldinger(sykmeldtEntry),
            )
        }
    }

    private fun getSykmeldinger(sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>) =
        sykmeldtEntry.value.distinctBy { it.sykmeldingId }.map { sykmeldtDbModel ->
            sykmeldtDbModel.toSykmelding()
        }

    private fun getDialogmoter(
        hendelserMap: Map<String, List<Hendelse>>,
        sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>
    ) = (
        hendelserMap[sykmeldtEntry.key.fnr]
            ?.filter { ma -> DialogmoteHendelser.contains(ma.oppgavetype) }
            ?.map {
                Dialogmote(
                    id = it.id,
                    hendelseId = it.hendelseId,
                    tekst = it.tekst ?: throw IllegalStateException("Dialogmøte uten tekst: ${it.id}")
                )
            }
            ?: emptyList()
        )

    private fun getPreviewSoknader(
        sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>,
        hendelserMap: Map<String, List<Hendelse>>
    ): List<PreviewSoknad> {
        val korrigerteSoknader = sykmeldtEntry.value
            .mapNotNull { it.soknad }
            .mapNotNull { it.korrigerer }

        return sykmeldtEntry.value
            .filter { it.soknad != null && !korrigerteSoknader.contains(it.soknad.id) }
            .mapNotNull { sykmeldt ->
                mapNullableSoknad(sykmeldt, getHendlersforSoknad(hendelserMap, sykmeldtEntry, sykmeldt))
            }
    }

    private fun getHendlersforSoknad(
        hendelserMap: Map<String, List<Hendelse>>,
        sykmeldtEntry: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>,
        sykmeldt: MinSykmeldtDbModel
    ) = hendelserMap[sykmeldtEntry.key.fnr]?.filter { it.id == sykmeldt.soknad?.id }.orEmpty()

    fun getSykmelding(sykmeldingId: String, lederFnr: String): Sykmelding? {
        return mineSykmeldteDb.getSykmelding(sykmeldingId, lederFnr)?.toSykmelding()
    }

    fun getSoknad(soknadId: String, lederFnr: String): Soknad? {
        return mineSykmeldteDb.getSoknad(soknadId, lederFnr)?.toSoknad()
    }

    fun markSykmeldingRead(sykmeldingId: String, lederFnr: String): Boolean {
        return mineSykmeldteDb.markSykmeldingRead(sykmeldingId, lederFnr)
    }

    fun markSoknadRead(soknadId: String, lederFnr: String): Boolean {
        return mineSykmeldteDb.markSoknadRead(soknadId, lederFnr)
    }

    fun markHendelseRead(hendelseId: UUID, lederFnr: String): Boolean {
        return mineSykmeldteDb.markHendelseRead(hendelseId, lederFnr)
    }
}

private fun isFriskmeldt(it: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>): Boolean {
    val latestTom: LocalDate = it.value
        .flatMap { it.sykmelding.sykmeldingsperioder }
        .maxOf { it.tom }

    return ChronoUnit.DAYS.between(latestTom, LocalDate.now()) > 16
}

private fun mapNullableSoknad(
    sykmeldtDbModel: MinSykmeldtDbModel,
    hendelser: List<Hendelse>
): PreviewSoknad? =
    sykmeldtDbModel.soknad?.let {
        toPreviewSoknad(it, sykmeldtDbModel.lestSoknad, hendelser)
    }

private fun MinSykmeldtDbModel.toMinSykmeldtKey(): MinSykmeldtKey = MinSykmeldtKey(
    narmestelederId = this.narmestelederId,
    orgnummer = this.orgnummer,
    navn = this.sykmeldtNavn,
    fnr = this.sykmeldtFnr,
    startDatoSykefravaer = this.startDatoSykefravar,
)

private fun Pair<SykmeldtDbModel, SoknadDbModel>.toSoknad(): Soknad {
    val (sykmeldt, soknadDb) = this

    val sykmeldingId = soknadDb.sykmeldingId

    requireNotNull(sykmeldingId) {
        "Søknad kan ikke eksistere uten sykmelding"
    }

    return Soknad(
        id = soknadDb.soknadId,
        sykmeldingId = sykmeldingId,
        navn = sykmeldt.pasientNavn,
        fnr = sykmeldt.pasientFnr,
        fom = soknadDb.soknad.fom!!,
        tom = soknadDb.tom,
        lest = soknadDb.lest,
        korrigererSoknadId = soknadDb.soknad.korrigerer,
        korrigertBySoknadId = soknadDb.soknad.korrigertAv,
        perioder = soknadDb.soknad.soknadsperioder?.map { it.toSoknadsperiode() }
            ?: throw IllegalStateException("Søknad uten perioder definert: ${soknadDb.soknadId}"),
        sporsmal = soknadDb.soknad.sporsmal?.map { it.toSporsmal() }
            ?: throw IllegalStateException("Søknad uten sporsmal definert: ${soknadDb.soknadId}")
    )
}

private fun MinSykmeldtDbModel.toSykmelding(): Sykmelding {
    val sykmelding = this.sykmelding

    return Sykmelding(
        id = sykmelding.id,
        kontaktDato = sykmelding.kontaktMedPasient.kontaktDato,
        fnr = this.sykmeldtFnr,
        lest = this.lestSykmelding,
        arbeidsgiver = Arbeidsgiver(
            navn = this.orgNavn,
            orgnummer = this.orgnummer,
        ),
        perioder = sykmelding.sykmeldingsperioder.map { it.toSykmeldingPeriode() },
        arbeidsforEtterPeriode = sykmelding.prognose?.arbeidsforEtterPeriode,
        hensynArbeidsplassen = sykmelding.prognose?.hensynArbeidsplassen,
        tiltakArbeidsplassen = sykmelding.tiltakArbeidsplassen,
        innspillArbeidsplassen = sykmelding.meldingTilArbeidsgiver,
        behandler = sykmelding.behandler.let {
            Behandler(
                navn = it.formatName(),
                hprNummer = it.hpr,
                telefon = it.tlf,
            )
        },
        startdatoSykefravar = this.startDatoSykefravar,
        navn = this.sykmeldtNavn,
    )
}

private fun Pair<SykmeldtDbModel, SykmeldingDbModel>.toSykmelding(): Sykmelding {
    val (sykmeldt, sykmelding) = this

    return Sykmelding(
        id = sykmelding.sykmeldingId,
        kontaktDato = sykmelding.sykmelding.kontaktMedPasient.kontaktDato,
        fnr = sykmelding.pasientFnr,
        lest = sykmelding.lest,
        arbeidsgiver = Arbeidsgiver(
            navn = sykmelding.orgnavn,
            orgnummer = sykmelding.orgnummer,
        ),
        perioder = sykmelding.sykmelding.sykmeldingsperioder.map { it.toSykmeldingPeriode() },
        arbeidsforEtterPeriode = sykmelding.sykmelding.prognose?.arbeidsforEtterPeriode,
        hensynArbeidsplassen = sykmelding.sykmelding.prognose?.hensynArbeidsplassen,
        tiltakArbeidsplassen = sykmelding.sykmelding.tiltakArbeidsplassen,
        innspillArbeidsplassen = sykmelding.sykmelding.meldingTilArbeidsgiver,
        behandler = sykmelding.sykmelding.behandler.let {
            Behandler(
                navn = it.formatName(),
                hprNummer = it.hpr,
                telefon = it.tlf,
            )
        },
        startdatoSykefravar = sykmeldt.startdatoSykefravaer,
        navn = sykmeldt.pasientNavn,
    )
}

private fun SykmeldingsperiodeAGDTO.toSykmeldingPeriode(): Periode =
    when (this.type) {
        PeriodetypeDTO.AKTIVITET_IKKE_MULIG -> AktivitetIkkeMulig(
            this.fom,
            this.tom,
            this.aktivitetIkkeMulig?.arbeidsrelatertArsak?.let {
                ArbeidsrelatertArsak(
                    beskrivelse = it.beskrivelse,
                    arsak = it.arsak.map { arsak ->
                        ArbeidsrelatertArsakEnum.valueOf(arsak.toString())
                    }
                )
            }
        )
        PeriodetypeDTO.AVVENTENDE -> Avventende(
            this.fom,
            this.tom,
            tilrettelegging = this.innspillTilArbeidsgiver,
        )
        PeriodetypeDTO.BEHANDLINGSDAGER -> Behandlingsdager(
            this.fom,
            this.tom,
            this.behandlingsdager ?: throw IllegalStateException("Behandlingsdager without behandlingsdager"),
        )
        PeriodetypeDTO.GRADERT -> {
            val gradering = this.gradert
            requireNotNull(gradering) {
                "Gradert periode uten gradert-data burde ikke eksistere"
            }

            Gradert(
                this.fom,
                this.tom,
                gradering.grad,
                gradering.reisetilskudd,
            )
        }
        PeriodetypeDTO.REISETILSKUDD -> Reisetilskudd(
            this.fom,
            this.tom,
        )
    }

private fun BehandlerAGDTO.formatName(): String = toFormattedNameString(fornavn, mellomnavn, etternavn)

private fun HendelseDbModel.toHendelse() =
    Hendelse(
        id = id,
        hendelseId = hendelseId,
        oppgavetype = safeParseHendelseEnum(oppgavetype),
        lenke = lenke,
        tekst = tekst
    )

fun safeParseHendelseEnum(oppgavetype: String): HendelseType {
    return try {
        HendelseType.valueOf(oppgavetype)
    } catch (e: Exception) {
        log.error("Ukjent oppgave av type $oppgavetype er ikke håndtert i applikasjonen. Mangler vi implementasjon?")
        HendelseType.UNKNOWN
    }
}
