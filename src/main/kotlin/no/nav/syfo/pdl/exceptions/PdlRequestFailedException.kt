package no.nav.syfo.pdl.exceptions

class PdlRequestFailedException(
    val statusCode: Int,
) : PdlPersonoppslagFailedException(
        message = "PDL svarte med HTTP-status $statusCode",
        retryable =
            statusCode == 408 ||
                statusCode == 425 ||
                statusCode == 429 ||
                statusCode in 500..599,
    )
