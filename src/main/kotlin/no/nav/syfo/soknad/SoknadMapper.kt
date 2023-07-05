package no.nav.syfo.soknad

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.helse.flex.sykepengesoknad.kafka.SporsmalDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.soknad.db.SoknadDbModel

const val ARBEID_UTENFOR_NORGE = "ARBEID_UTENFOR_NORGE"
const val ANDRE_INNTEKTSKILDER = "ANDRE_INNTEKTSKILDER"
const val ANDRE_INNTEKTSKILDER_V2 = "ANDRE_INNTEKTSKILDER_V2"
const val UTENLANDSK_SYKMELDING_BOSTED = "UTENLANDSK_SYKMELDING_BOSTED"
const val UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE =
    "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE"
const val UTENLANDSK_SYKMELDING_TRYGD_UTENFOR_NORGE = "UTENLANDSK_SYKMELDING_TRYGD_UTENFOR_NORGE"
const val YRKESSKADE = "YRKESSKADE"
const val YRKESSKADE_V2 = "YRKESSKADE_V2"

fun SykepengesoknadDTO.toSoknadDbModel(): SoknadDbModel {
    return SoknadDbModel(
        soknadId = id,
        sykmeldingId = sykmeldingId,
        pasientFnr = fnr,
        orgnummer = arbeidsgiver?.orgnummer
                ?: throw IllegalStateException("Har mottatt sendt søknad uten orgnummer: $id"),
        soknad = tilArbeidsgiverSoknad(),
        sendtDato = sendtArbeidsgiver?.toLocalDate(),
        lest = false, // oppdateres fra strangler
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
        tom = tom!!,
    )
}

fun SykepengesoknadDTO.tilArbeidsgiverSoknad(): SykepengesoknadDTO =
    copy(
        andreInntektskilder = null,
        sporsmal =
            sporsmal
                ?.fjernSporsmalOmAndreInnntektsKilder()
                ?.fjernSporsmalOmArbeidUtenforNorge()
                ?.fjernSporsmalFraUtenlandskSykmelding()
                ?.fjernSporsmalOmArbeidYrkesskade(),
    )

fun List<SporsmalDTO>.fjernSporsmalOmAndreInnntektsKilder() =
    this.fjernSporsmal(ANDRE_INNTEKTSKILDER).fjernSporsmal(ANDRE_INNTEKTSKILDER_V2)

fun List<SporsmalDTO>.fjernSporsmalOmArbeidUtenforNorge() = this.fjernSporsmal(ARBEID_UTENFOR_NORGE)

fun List<SporsmalDTO>.fjernSporsmalOmArbeidYrkesskade() =
    this.fjernSporsmal(YRKESSKADE).fjernSporsmal(YRKESSKADE_V2)

fun List<SporsmalDTO>.fjernSporsmalFraUtenlandskSykmelding() =
    this.fjernSporsmal(UTENLANDSK_SYKMELDING_BOSTED)
        .fjernSporsmal(UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE)
        .fjernSporsmal(UTENLANDSK_SYKMELDING_TRYGD_UTENFOR_NORGE)

fun List<SporsmalDTO>.fjernSporsmal(tag: String): List<SporsmalDTO> = fjernSporsmalHjelper(tag)

fun List<SporsmalDTO>.fjernSporsmalHjelper(tag: String): List<SporsmalDTO> =
    fjernSporsmalHjelper(tag, this)

private fun fjernSporsmalHjelper(tag: String, sporsmal: List<SporsmalDTO>): List<SporsmalDTO> =
    sporsmal
        .filterNot { it.tag == tag }
        .map {
            it.copy(
                undersporsmal = it.undersporsmal?.let { us -> fjernSporsmalHjelper(tag, us) },
            )
        }
