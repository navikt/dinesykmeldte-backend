package no.nav.syfo.sykmelding.model.sykmelding.kafka

import java.time.LocalDate
import java.time.OffsetDateTime

data class EnkelSykmelding(
    val id: String,
    val mottattTidspunkt: OffsetDateTime,
    val legekontorOrgnummer: String?,
    val behandletTidspunkt: OffsetDateTime,
    val meldingTilArbeidsgiver: String?,
    val navnFastlege: String?,
    val tiltakArbeidsplassen: String?,
    val syketilfelleStartDato: LocalDate?,
    val behandler: no.nav.syfo.sykmelding.model.sykmelding.model.BehandlerDTO,
    val sykmeldingsperioder:
        List<no.nav.syfo.sykmelding.model.sykmelding.model.SykmeldingsperiodeDTO>,
    val arbeidsgiver: no.nav.syfo.sykmelding.model.sykmelding.model.ArbeidsgiverDTO,
    val kontaktMedPasient: no.nav.syfo.sykmelding.model.sykmelding.model.KontaktMedPasientDTO,
    val prognose: no.nav.syfo.sykmelding.model.sykmelding.model.PrognoseDTO?,
    val egenmeldt: Boolean,
    val papirsykmelding: Boolean,
    val harRedusertArbeidsgiverperiode: Boolean,
    val merknader: List<no.nav.syfo.sykmelding.model.Merknad>?,
)
