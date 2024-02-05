package no.nav.syfo.soknad.model

data class Sporsmal(
    val id: String,
    val tag: String,
    val min: String?,
    val max: String?,
    val kriterieForVisningAvUndersporsmal: Visningskriterium?,
    val svartype: Svartype,
    val sporsmalstekst: String?,
    val undertekst: String?,
    val svar: List<Svar>,
    val undersporsmal: List<Sporsmal>,
)
