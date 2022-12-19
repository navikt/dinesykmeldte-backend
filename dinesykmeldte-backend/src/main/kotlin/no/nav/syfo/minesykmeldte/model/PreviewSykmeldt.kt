package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class PreviewSykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val orgnavn: String,
    val fnr: String,
    val navn: String,
    val startdatoSykefravar: LocalDate,
    val friskmeldt: Boolean,
    val sykmeldinger: List<Sykmelding>,
    val previewSoknader: List<PreviewSoknad>,
    val dialogmoter: List<Dialogmote>,
    val aktivitetsvarsler: List<Aktivitetsvarsel>,
    val oppfolgingsplaner: List<Oppfolgingsplan>
)

data class Oppfolgingsplan(
    val hendelseId: UUID,
    var tekst: String,
    var mottatt: OffsetDateTime?
)

data class Dialogmote(
    val hendelseId: UUID,
    var tekst: String,
    var mottatt: OffsetDateTime?
)

data class Aktivitetsvarsel(
    val hendelseId: UUID,
    val mottatt: OffsetDateTime,
    val lest: OffsetDateTime?
)
