package no.nav.syfo.sykmelding.model.sykmelding.model

enum class MedisinskArsakTypeDTO(val text: String) {
    TILSTAND_HINDRER_AKTIVITET("Helsetilstanden hindrer pasienten i å være i aktivitet"),
    AKTIVITET_FORVERRER_TILSTAND("Aktivitet vil forverre helsetilstanden"),
    AKTIVITET_FORHINDRER_BEDRING("Aktivitet vil hindre/forsinke bedring av helsetilstanden"),
    ANNET("Annet")
}
