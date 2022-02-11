package no.nav.syfo.minesykmeldte.model

import no.nav.helse.flex.sykepengesoknad.kafka.FravarstypeDTO
import java.time.LocalDate
import no.nav.syfo.kafka.felles.SvartypeDTO
import no.nav.syfo.kafka.felles.VisningskriteriumDTO

data class Soknad(
    val id: String,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val navn: String,
    val fnr: String,
    val lest: Boolean,
    val korrigertBySoknadId: String?,
    val fravar: List<Fravar>,
    val perioder: List<Soknadsperiode>,
    val sporsmal: List<Sporsmal>,
)

data class Fravar(
    val fom: LocalDate,
    val tom: LocalDate,
    val type: FravarstypeDTO,
)

data class Soknadsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val sykmeldingsgrad: Int?,
    val sykmeldingstype: PeriodeEnum,
)

data class Sporsmal(
    val id: String,
    val tag: String,
    val sporsmalstekst: String,
    val undertekst: String?,
    val svartype: SvartypeDTO,
    var kriterieForVisningAvUndersporsmal: VisningskriteriumDTO,
    var svar: List<Svar>,
    var undersporsmal: List<Sporsmal>?,
)

data class Svar(
    var verdi: String,
)
