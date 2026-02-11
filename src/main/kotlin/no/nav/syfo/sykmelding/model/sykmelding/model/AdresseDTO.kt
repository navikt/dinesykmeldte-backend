package no.nav.syfo.sykmelding.model.sykmelding.model

data class AdresseDTO(
    val gate: String?,
    val postnummer: Int?,
    val kommune: String?,
    val postboks: String?,
    val land: String?,
)
