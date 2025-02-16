package no.nav.syfo.sykmelding.model.sykmelding.arbeidsgiver

import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.sykmelding.model.Merknad

data class ArbeidsgiverSykmelding(
    val id: String,
    val mottattTidspunkt: OffsetDateTime,
    val syketilfelleStartDato: LocalDate?,
    val behandletTidspunkt: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverAGDTO,
    val sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>,
    val prognose: PrognoseAGDTO?,
    val tiltakArbeidsplassen: String?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasientAGDTO,
    val behandler: BehandlerAGDTO?,
    val egenmeldt: Boolean,
    val papirsykmelding: Boolean,
    val harRedusertArbeidsgiverperiode: Boolean,
    val merknader: List<Merknad>?,
    val utenlandskSykmelding: UtenlandskSykmeldingAGDTO?,
    val signaturDato: OffsetDateTime?,
)
