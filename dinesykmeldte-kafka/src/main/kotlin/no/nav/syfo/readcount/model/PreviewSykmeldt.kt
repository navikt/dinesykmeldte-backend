package no.nav.syfo.readcount.model

data class PreviewSykmeldt(
    val narmestelederId: String,
    val antallUlesteSykmeldinger: Int,
    val antallUlesteSoknader: Int,
    val antallDialogmoter: Int,
    val antallAktivitetsvarsler: Int,
    val antallOppfolgingsplaner: Int
)
