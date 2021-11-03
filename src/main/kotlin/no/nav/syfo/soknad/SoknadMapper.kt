package no.nav.syfo.soknad

import no.nav.syfo.kafka.felles.SporsmalDTO
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.soknad.db.SoknadDbModel
import java.time.OffsetDateTime
import java.time.ZoneOffset

const val ARBEID_UTENFOR_NORGE = "ARBEID_UTENFOR_NORGE"
const val ANDRE_INNTEKTSKILDER = "ANDRE_INNTEKTSKILDER"

fun SykepengesoknadDTO.toSoknadDbModel(): SoknadDbModel {
    return SoknadDbModel(
        soknadId = id,
        sykmeldingId = sykmeldingId,
        pasientFnr = fnr,
        orgnummer = arbeidsgiver?.orgnummer
            ?: throw IllegalStateException("Har mottatt sendt søknad uten orgnummer: $id"),
        soknad = tilArbeidsgiverSoknad(),
        sendtDato = sendtArbeidsgiver!!.toLocalDate(),
        lest = false, // oppdateres fra strangler
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
        tom = tom!!
    )
}

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
