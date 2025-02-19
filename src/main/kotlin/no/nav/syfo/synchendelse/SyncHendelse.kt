package no.nav.syfo.synchendelse

enum class SyncHendelseType {
    SYKMELDING,
    SOKNAD,
    HENDELSE
}

enum class SyncSource {
    ESYFO,
    TSM,
}

data class SyncHendelse(
    val id: List<String>,
    val type: SyncHendelseType,
    val source: SyncSource = SyncSource.TSM,
)
