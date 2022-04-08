package no.nav.syfo.minesykmeldte.model

import java.time.OffsetDateTime
import java.util.UUID

enum class HendelseType {
    DIALOGMOTE_INNKALLING,
    DIALOGMOTE_AVLYSNING,
    DIALOGMOTE_ENDRING,
    DIALOGMOTE_REFERAT,
    DIALOGMOTE_SVAR_BEHOV,
    DIALOGMOTE_6_UKERS_VARSEL,
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
    // TODO må endres når eSYFO bestemmer hva den skal hete
    HendelseType.DIALOGMOTE_6_UKERS_VARSEL,
)

val OppfolgningsplanerHendelser = listOf(
    HendelseType.OPPFOLGINGSPLAN_OPPRETTET,
    HendelseType.OPPFOLGINGSPLAN_TIL_GODKJENNING
)

data class Hendelse(
    val id: String,
    val oppgavetype: HendelseType,
    val lenke: String?,
    val tekst: String?,
    val hendelseId: UUID,
    val mottatt: OffsetDateTime,
    val ferdigstilt: OffsetDateTime?,
)
