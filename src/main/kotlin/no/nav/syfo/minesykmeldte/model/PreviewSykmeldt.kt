package no.nav.syfo.minesykmeldte.model

import java.time.LocalDate
import java.time.LocalDateTime

data class PreviewSykmeldt(
    val narmestelederId: String,
    val orgnummer: String,
    val fnr: String,
    val navn: String,
    val startdatoSykefravar: LocalDate,
    val friskmeldt: Boolean,
    val previewSykmeldinger: List<PreviewSykmelding>,
    val previewSoknader: List<PreviewSoknad>
)

data class PreviewSykmelding(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val type: String,
    val lest: Boolean
)

enum class SoknadStatus {
    FREMTIDIG,
    NY,
    SENDT,
    KORRIGERT
}

sealed interface PreviewSoknad {
    val id: String
    val sykmeldingId: String?
    val fom: LocalDate?
    val tom: LocalDate?
    val status: SoknadStatus
}

data class SendtSoknad(
    val korrigertBySoknadId: String?,
    val lest: Boolean,
    val sendtDato: LocalDateTime,
    override val id: String,
    override val sykmeldingId: String?,
    override val fom: LocalDate?,
    override val tom: LocalDate?,
) : PreviewSoknad {
    override val status = SoknadStatus.SENDT
}

data class NySoknad(
    val frist: LocalDate,
    val varsel: Boolean,
    override val id: String,
    override val sykmeldingId: String?,
    override val fom: LocalDate?,
    override val tom: LocalDate?,
) : PreviewSoknad {
    override val status = SoknadStatus.NY
}

data class FremtidigSoknad(
    override val id: String,
    override val sykmeldingId: String?,
    override val fom: LocalDate?,
    override val tom: LocalDate?,
) : PreviewSoknad {
    override val status = SoknadStatus.FREMTIDIG
}

data class KorrigertSoknad(
    val korrigertBySoknadId: String?,
    val korrigererSoknadId: String,
    override val id: String,
    override val sykmeldingId: String?,
    override val fom: LocalDate?,
    override val tom: LocalDate?,
) : PreviewSoknad {
    override val status = SoknadStatus.KORRIGERT
}
