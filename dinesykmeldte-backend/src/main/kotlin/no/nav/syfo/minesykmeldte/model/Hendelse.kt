package no.nav.syfo.minesykmeldte.model

import java.util.UUID

enum class HendelseType {
    DIALOGMOTE_INNKALLING,
    DIALOGMOTE_AVLYSNING,
    DIALOGMOTE_ENDRING,
    DIALOGMOTE_REFERAT,
    DIALOGMOTE_SVAR_BEHOV,
    IKKE_SENDT_SOKNAD,
    UNKNOWN,
}

val DialogmoteHendelser = listOf(
    HendelseType.DIALOGMOTE_INNKALLING,
    HendelseType.DIALOGMOTE_AVLYSNING,
    HendelseType.DIALOGMOTE_ENDRING,
    HendelseType.DIALOGMOTE_REFERAT,
    HendelseType.DIALOGMOTE_SVAR_BEHOV,
)

data class Hendelse(
    val id: String,
    val oppgavetype: HendelseType,
    val lenke: String?,
    val tekst: String?,
    val hendelseId: UUID,
)
