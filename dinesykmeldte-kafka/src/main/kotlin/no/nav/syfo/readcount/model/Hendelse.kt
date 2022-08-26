package no.nav.syfo.readcount.model

import java.time.OffsetDateTime

enum class HendelseType {
    DIALOGMOTE_INNKALLING,
    DIALOGMOTE_AVLYSNING,
    DIALOGMOTE_ENDRING,
    DIALOGMOTE_REFERAT,
    DIALOGMOTE_SVAR_BEHOV,
    AKTIVITETSKRAV,
    IKKE_SENDT_SOKNAD,
    OPPFOLGINGSPLAN_OPPRETTET,
    OPPFOLGINGSPLAN_TIL_GODKJENNING,
    UNKNOWN,
}

val DialogmoteHendelser = listOf(
    HendelseType.DIALOGMOTE_INNKALLING,
    HendelseType.DIALOGMOTE_AVLYSNING,
    HendelseType.DIALOGMOTE_ENDRING,
    HendelseType.DIALOGMOTE_REFERAT,
    HendelseType.DIALOGMOTE_SVAR_BEHOV,
)

val OppfolgingsplanerHendelser = listOf(
    HendelseType.OPPFOLGINGSPLAN_OPPRETTET,
    HendelseType.OPPFOLGINGSPLAN_TIL_GODKJENNING
)

data class Hendelse(
    val id: String,
    val oppgavetype: HendelseType,
    val ferdigstilt: OffsetDateTime?,
)
