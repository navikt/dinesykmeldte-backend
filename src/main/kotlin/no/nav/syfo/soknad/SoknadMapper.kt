package no.nav.syfo.soknad

import no.nav.syfo.kafka.felles.SporsmalDTO
import no.nav.syfo.kafka.felles.SykepengesoknadDTO

const val ARBEID_UTENFOR_NORGE = "ARBEID_UTENFOR_NORGE"
const val ANDRE_INNTEKTSKILDER = "ANDRE_INNTEKTSKILDER"

fun SykepengesoknadDTO.tilArbeidsgiverSoknad(): SykepengesoknadDTO =
    copy(
        andreInntektskilder = null,
        sporsmal = sporsmal?.fjernSporsmalOmAndreInnntektsKilder()?.fjernSporsmalOmArbeidUtenforNorge()
    )

fun List<SporsmalDTO>.fjernSporsmalOmAndreInnntektsKilder() =
    this.fjernSporsmal(ANDRE_INNTEKTSKILDER)

fun List<SporsmalDTO>.fjernSporsmalOmArbeidUtenforNorge() =
    this.fjernSporsmal(ARBEID_UTENFOR_NORGE)

fun List<SporsmalDTO>.fjernSporsmal(tag: String): List<SporsmalDTO> =
    fjernSporsmalHjelper(tag)

fun List<SporsmalDTO>.fjernSporsmalHjelper(tag: String): List<SporsmalDTO> =
    fjernSporsmalHjelper(tag, this)

private fun fjernSporsmalHjelper(tag: String, sporsmal: List<SporsmalDTO>): List<SporsmalDTO> =
    sporsmal
        .filterNot { it.tag == tag }
        .map {
            it.copy(
                undersporsmal = it.undersporsmal?.let { us -> fjernSporsmalHjelper(tag, us) }
            )
        }
