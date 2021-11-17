package no.nav.syfo.util

import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun createArbeidsgiverSykmelding(
    sykmeldingId: String,
    perioder: List<SykmeldingsperiodeAGDTO> = listOf(createSykmeldingsperiode())
) = ArbeidsgiverSykmelding(
    id = sykmeldingId,
    mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
    syketilfelleStartDato = null,
    behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
    arbeidsgiver = ArbeidsgiverAGDTO(null, null),
    sykmeldingsperioder = perioder,
    prognose = null,
    tiltakArbeidsplassen = null,
    meldingTilArbeidsgiver = null,
    kontaktMedPasient = KontaktMedPasientAGDTO(null),
    behandler = BehandlerAGDTO("Fornavn", null, "Etternavn", null, AdresseDTO(null, null, null, null, null), null),
    egenmeldt = false,
    papirsykmelding = false,
    harRedusertArbeidsgiverperiode = false,
    merknader = null
)

fun createSykmeldingsperiode(
    fom: LocalDate = LocalDate.now().minusDays(2),
    tom: LocalDate = LocalDate.now().plusDays(10),
    gradert: GradertDTO? = null,
    behandlingsdager: Int? = null,
    innspillTilArbeidsgiver: String? = null,
    type: PeriodetypeDTO = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
    aktivitetIkkeMulig: AktivitetIkkeMuligAGDTO? = AktivitetIkkeMuligAGDTO(null),
    reisetilskudd: Boolean = false
) = SykmeldingsperiodeAGDTO(
    fom = fom,
    tom = tom,
    gradert = gradert,
    behandlingsdager = behandlingsdager,
    innspillTilArbeidsgiver = innspillTilArbeidsgiver,
    type = type,
    aktivitetIkkeMulig = aktivitetIkkeMulig,
    reisetilskudd = reisetilskudd,
)
