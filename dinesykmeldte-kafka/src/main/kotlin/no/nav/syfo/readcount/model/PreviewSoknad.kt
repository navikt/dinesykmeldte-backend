package no.nav.syfo.readcount.model

sealed interface PreviewSoknad {
    val id: String
    val status: SoknadStatus
}

enum class SoknadStatus {
    FREMTIDIG,
    NY,
    SENDT
}

data class PreviewSendtSoknad(
    val korrigererSoknadId: String?,
    val lest: Boolean,
    override val id: String,
) : PreviewSoknad {
    override val status = SoknadStatus.SENDT
}

data class PreviewNySoknad(
    val lest: Boolean,
    val ikkeSendtSoknadVarsel: Boolean,
    override val id: String,
) : PreviewSoknad {
    override val status = SoknadStatus.NY
}

data class PreviewFremtidigSoknad(
    override val id: String,
) : PreviewSoknad {
    override val status = SoknadStatus.FREMTIDIG
}
