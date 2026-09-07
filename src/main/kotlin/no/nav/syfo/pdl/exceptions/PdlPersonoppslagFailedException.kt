package no.nav.syfo.pdl.exceptions

open class PdlPersonoppslagFailedException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class PdlResponseIncompleteException(
    message: String,
) : PdlPersonoppslagFailedException(message, retryable = false)
