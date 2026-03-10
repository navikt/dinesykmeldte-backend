package no.nav.syfo.pdl.exceptions

class PdlRequestFailedException(
    override val message: String?,
) : RuntimeException(message)
