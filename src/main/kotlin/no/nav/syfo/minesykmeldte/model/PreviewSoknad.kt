package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate
import java.time.LocalDateTime

sealed interface PreviewSoknad {
    val id: String
    val sykmeldingId: String?
    val fom: LocalDate?
    val tom: LocalDate?
    val status: SoknadStatus
    val perioder: List<Soknadsperiode>
}

enum class SoknadStatus {
    FREMTIDIG,
    NY,
    SENDT,
    KORRIGERT
}

data class PreviewSendtSoknad(
    val korrigertBySoknadId: String?,
    val lest: Boolean,
    val sendtDato: LocalDateTime,
    override val id: String,
    override val sykmeldingId: String?,
    override val fom: LocalDate?,
    override val tom: LocalDate?,
    override val perioder: List<Soknadsperiode>,
) : PreviewSoknad {
    override val status = SoknadStatus.SENDT
}

data class PreviewNySoknad(
    val frist: LocalDate,
    val varsel: Boolean,
    override val id: String,
    override val sykmeldingId: String?,
    override val fom: LocalDate?,
    override val tom: LocalDate?,
    override val perioder: List<Soknadsperiode>,
) : PreviewSoknad {
    override val status = SoknadStatus.NY
}

data class PreviewFremtidigSoknad(
    override val id: String,
    override val sykmeldingId: String?,
    override val fom: LocalDate?,
    override val tom: LocalDate?,
    override val perioder: List<Soknadsperiode>,
) : PreviewSoknad {
    override val status = SoknadStatus.FREMTIDIG
}

data class PreviewKorrigertSoknad(
    val korrigertBySoknadId: String?,
    val korrigererSoknadId: String,
    override val id: String,
    override val sykmeldingId: String?,
    override val fom: LocalDate?,
    override val tom: LocalDate?,
    override val perioder: List<Soknadsperiode>,
) : PreviewSoknad {
    override val status = SoknadStatus.KORRIGERT
}