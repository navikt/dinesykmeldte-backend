package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.flex.sykepengesoknad.kafka.SvartypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.VisningskriteriumDTO

data class Soknad(
    val id: String,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val navn: String,
    val fnr: String,
    val lest: Boolean,
    val sendtDato: LocalDateTime,
    val sendtTilNavDato: LocalDateTime?,
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

data class Undersporsmal(
    val id: String,
    val tag: String,
    val min: String?,
    val max: String?,
    val sporsmalstekst: String?,
    val undertekst: String?,
    val svartype: SvartypeDTO,
    var kriterieForVisningAvUndersporsmal: VisningskriteriumDTO?,
    var svar: List<Svar>?,
    var undersporsmal: List<Undersporsmal>?,
)

data class Sporsmal(
    val id: String,
    val tag: String,
    val min: String?,
    val max: String?,
    val sporsmalstekst: String,
    val undertekst: String?,
    val svartype: SvartypeDTO,
    var kriterieForVisningAvUndersporsmal: VisningskriteriumDTO?,
    var svar: List<Svar>?,
    var undersporsmal: List<Undersporsmal>?,
)

data class Svar(
    var verdi: String,
)
