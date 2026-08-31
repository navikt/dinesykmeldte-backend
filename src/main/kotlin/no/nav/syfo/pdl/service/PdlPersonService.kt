package no.nav.syfo.pdl.service

import kotlinx.coroutines.CancellationException
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.ResponseError
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.exceptions.PdlPersonoppslagFailedException
import no.nav.syfo.pdl.exceptions.PdlRequestFailedException
import no.nav.syfo.pdl.exceptions.PdlResponseIncompleteException
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.logger

internal const val PDL_PERSONOPPSLAG_FAILED = "pdl_personoppslag_failed"
internal const val PDL_PERSONOPPSLAG_PARTIAL_RESPONSE = "pdl_personoppslag_partial_response"
internal const val HENT_PERSONOPPLYSNINGER = "hent_personopplysninger"

internal enum class PdlPersonoppslagErrorCode {
    PDL_UNAUTHENTICATED,
    PDL_UNAUTHORIZED,
    PDL_NOT_FOUND,
    PDL_BAD_REQUEST,
    PDL_SERVER_ERROR,
    PDL_GRAPHQL_ERROR,
    PDL_UNKNOWN_ERROR,
    PDL_MULTIPLE_ERRORS,
    PDL_ACCESS_TOKEN_FAILED,
    PDL_HTTP_ERROR,
    PDL_REQUEST_FAILED,
    PDL_RESPONSE_INCOMPLETE,
}

private val pdlErrorCodeMapping =
    mapOf(
        "unauthenticated" to PdlPersonoppslagErrorCode.PDL_UNAUTHENTICATED,
        "unauthorized" to PdlPersonoppslagErrorCode.PDL_UNAUTHORIZED,
        "not_found" to PdlPersonoppslagErrorCode.PDL_NOT_FOUND,
        "bad_request" to PdlPersonoppslagErrorCode.PDL_BAD_REQUEST,
        "server_error" to PdlPersonoppslagErrorCode.PDL_SERVER_ERROR,
    )

internal fun List<ResponseError>.toPdlPersonoppslagErrorCode(): PdlPersonoppslagErrorCode =
    map { error ->
        val runtimeCode = error.extensions?.code
        when {
            runtimeCode == null -> PdlPersonoppslagErrorCode.PDL_GRAPHQL_ERROR
            runtimeCode in pdlErrorCodeMapping -> pdlErrorCodeMapping.getValue(runtimeCode)
            else -> PdlPersonoppslagErrorCode.PDL_UNKNOWN_ERROR
        }
    }.toSet()
        .let { mappedCodes ->
            when (mappedCodes.size) {
                0 -> PdlPersonoppslagErrorCode.PDL_UNKNOWN_ERROR
                1 -> mappedCodes.single()
                else -> PdlPersonoppslagErrorCode.PDL_MULTIPLE_ERRORS
            }
        }

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClient: AccessTokenClient,
    private val pdlScope: String,
) {
    companion object {
        const val AKTORID_GRUPPE = "AKTORID"
    }

    private val log = logger()

    suspend fun getPerson(fnr: String): PdlPerson {
        val accessToken =
            try {
                accessTokenClient.getAccessToken(pdlScope)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val errorMessage = "Klarte ikke å hente tilgangstoken for PDL"
                logPersonoppslagFailed(
                    errorCode = PdlPersonoppslagErrorCode.PDL_ACCESS_TOKEN_FAILED,
                    message = errorMessage,
                )
                throw PdlPersonoppslagFailedException(
                    message = errorMessage,
                    cause = exception,
                )
            }

        val pdlResponse =
            try {
                pdlClient.getPerson(fnr = fnr, token = accessToken)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val errorCode =
                    if (exception is PdlRequestFailedException) {
                        PdlPersonoppslagErrorCode.PDL_HTTP_ERROR
                    } else {
                        PdlPersonoppslagErrorCode.PDL_REQUEST_FAILED
                    }
                logPersonoppslagFailed(
                    errorCode = errorCode,
                    upstreamStatus = (exception as? PdlRequestFailedException)?.statusCode,
                )
                throw if (exception is PdlPersonoppslagFailedException) {
                    exception
                } else {
                    PdlPersonoppslagFailedException(
                        message = "Klarte ikke å hente personopplysninger fra PDL",
                        cause = exception,
                    )
                }
            }

        val pdlErrors = pdlResponse.errors.orEmpty()
        val person =
            try {
                pdlResponse.toPerson()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val errorCode =
                    if (pdlErrors.isEmpty()) {
                        PdlPersonoppslagErrorCode.PDL_RESPONSE_INCOMPLETE
                    } else {
                        pdlErrors.toPdlPersonoppslagErrorCode()
                    }
                if (pdlErrors.isEmpty()) {
                    logPersonoppslagFailed(
                        errorCode = errorCode,
                        message = "PDL-responsen manglet nødvendige personopplysninger",
                    )
                } else {
                    logPersonoppslagFailed(pdlErrors)
                }
                throw when {
                    pdlErrors.isEmpty() -> exception
                    errorCode == PdlPersonoppslagErrorCode.PDL_NOT_FOUND ->
                        NameNotFoundInPdlException("Fant ikke person i PDL")
                    else ->
                        PdlPersonoppslagFailedException(
                            message = "PDL returnerte feil ved personoppslag",
                            cause = exception,
                        )
                }
            }

        if (pdlErrors.isNotEmpty()) {
            logPartialPersonoppslag(pdlErrors)
        }

        return person
    }

    private fun GetPersonResponse.toPerson(): PdlPerson {
        val navn = data?.person?.navn?.firstOrNull()
        val aktorId =
            data
                ?.identer
                ?.identer
                ?.firstOrNull { it.gruppe == AKTORID_GRUPPE }
                ?.ident

        if (navn == null) {
            throw PdlResponseIncompleteException("Fant ikke navn i PDL-respons")
        }
        if (aktorId == null) {
            throw PdlResponseIncompleteException("Fant ikke aktør-ID i PDL")
        }

        return PdlPerson(
            navn =
                Navn(
                    fornavn = navn.fornavn,
                    mellomnavn = navn.mellomnavn,
                    etternavn = navn.etternavn,
                ),
        )
    }

    private fun logPersonoppslagFailed(pdlErrors: List<ResponseError>) {
        val errorCode = pdlErrors.toPdlPersonoppslagErrorCode()
        log
            .atError()
            .addKeyValue("event_type", PDL_PERSONOPPSLAG_FAILED)
            .addKeyValue("error_code", errorCode.name)
            .addKeyValue("operation", HENT_PERSONOPPLYSNINGER)
            .addKeyValue("pdl_errors", pdlErrors)
            .log("Klarte ikke å fullføre personoppslag i PDL")
    }

    private fun logPersonoppslagFailed(
        errorCode: PdlPersonoppslagErrorCode,
        upstreamStatus: Int? = null,
        message: String = "Klarte ikke å hente personopplysninger fra PDL",
    ) {
        val logBuilder =
            log
                .atError()
                .addKeyValue("event_type", PDL_PERSONOPPSLAG_FAILED)
                .addKeyValue("error_code", errorCode.name)
                .addKeyValue("operation", HENT_PERSONOPPLYSNINGER)
        if (upstreamStatus != null && upstreamStatus in 100..599) {
            logBuilder.addKeyValue("upstream_status", upstreamStatus)
        }
        logBuilder.log(message)
    }

    private fun logPartialPersonoppslag(pdlErrors: List<ResponseError>) {
        log
            .atWarn()
            .addKeyValue("event_type", PDL_PERSONOPPSLAG_PARTIAL_RESPONSE)
            .addKeyValue("error_code", pdlErrors.toPdlPersonoppslagErrorCode().name)
            .addKeyValue("operation", HENT_PERSONOPPLYSNINGER)
            .addKeyValue("pdl_errors", pdlErrors)
            .log("PDL returnerte feil sammen med brukbare personopplysninger")
    }
}
