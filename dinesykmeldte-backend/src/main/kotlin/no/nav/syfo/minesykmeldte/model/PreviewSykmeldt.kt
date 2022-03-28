package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate
import java.util.UUID

data class PreviewSykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String,
    val startdatoSykefravar: LocalDate,
    val friskmeldt: Boolean,
    val sykmeldinger: List<Sykmelding>,
    val previewSoknader: List<PreviewSoknad>,
    val dialogmoter: List<Dialogmote>,
)

data class Dialogmote(
    val id: String,
    val hendelseId: UUID,
    var tekst: String,
)
