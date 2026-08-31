package no.nav.syfo.pdl.exceptions

class PdlRequestFailedException(
    val statusCode: Int,
) : PdlPersonoppslagFailedException("PDL svarte med HTTP-status $statusCode")
