package no.nav.syfo.minesykmeldte.model

import no.nav.helse.flex.sykepengesoknad.kafka.SvartypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.VisningskriteriumDTO
import java.time.LocalDate

data class Soknad(
    val id: String,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val navn: String,
    val fnr: String,
    val lest: Boolean,
    val korrigererSoknadId: String?,
    val korrigertBySoknadId: String?,
    val perioder: List<Soknadsperiode>,
    val sporsmal: List<Sporsmal>,
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
