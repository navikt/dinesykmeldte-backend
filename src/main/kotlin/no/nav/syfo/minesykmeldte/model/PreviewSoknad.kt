package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

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
}

data class PreviewSendtSoknad(
    val korrigererSoknadId: String?,
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
    val lest: Boolean,
    val ikkeSendtSoknadVarsel: Boolean,
    val ikkeSendtSoknadVarsletDato: OffsetDateTime?,
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
