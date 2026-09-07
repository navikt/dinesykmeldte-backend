package no.nav.syfo.pdl.service

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.azuread.AccessTokenRequestFailedException
import no.nav.syfo.common.exception.ServiceUnavailableException
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.exceptions.PdlPersonoppslagFailedException
import no.nav.syfo.pdl.exceptions.PdlRequestFailedException
import no.nav.syfo.pdl.exceptions.PdlResponseIncompleteException
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.logger
import no.nav.syfo.util.objectMapper
import java.io.IOException

internal const val PDL_PERSONOPPSLAG_FAILED = "pdl_personoppslag_failed"
internal const val PDL_PERSONOPPSLAG_PARTIAL_RESPONSE = "pdl_personoppslag_partial_response"
internal const val PDL_PERSONOPPSLAG_NOT_FOUND = "pdl_personoppslag_not_found"
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

internal fun List<JsonNode>.toPdlPersonoppslagErrorCode(): PdlPersonoppslagErrorCode =
    map { error ->
        val extensions = error.get("extensions")
        val runtimeCode = extensions?.get("code")
        when {
            extensions == null || extensions.isNull -> PdlPersonoppslagErrorCode.PDL_GRAPHQL_ERROR
            runtimeCode?.isTextual != true -> PdlPersonoppslagErrorCode.PDL_UNKNOWN_ERROR
            runtimeCode.asText() in pdlErrorCodeMapping ->
                pdlErrorCodeMapping.getValue(runtimeCode.asText())
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

internal fun PdlPersonoppslagErrorCode.isRetryableGraphQlResponse(): Boolean =
    when (this) {
        PdlPersonoppslagErrorCode.PDL_SERVER_ERROR,
        -> true

        PdlPersonoppslagErrorCode.PDL_UNAUTHENTICATED,
        PdlPersonoppslagErrorCode.PDL_UNAUTHORIZED,
        PdlPersonoppslagErrorCode.PDL_NOT_FOUND,
        PdlPersonoppslagErrorCode.PDL_BAD_REQUEST,
        PdlPersonoppslagErrorCode.PDL_GRAPHQL_ERROR,
        PdlPersonoppslagErrorCode.PDL_UNKNOWN_ERROR,
        PdlPersonoppslagErrorCode.PDL_MULTIPLE_ERRORS,
        PdlPersonoppslagErrorCode.PDL_RESPONSE_INCOMPLETE,
        -> false

        PdlPersonoppslagErrorCode.PDL_ACCESS_TOKEN_FAILED,
        PdlPersonoppslagErrorCode.PDL_HTTP_ERROR,
        PdlPersonoppslagErrorCode.PDL_REQUEST_FAILED,
        -> error("Retry-policy for $this avgjøres ved den eksterne feilgrensen")
    }

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClient: AccessTokenClient,
    private val pdlScope: String,
) {
    private val log = logger()

    suspend fun getPerson(fnr: String): PdlPerson {
        val accessToken =
            try {
                accessTokenClient.getAccessToken(pdlScope)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val failure = exception.toAccessTokenFailure() ?: throw exception
                val errorMessage = "Klarte ikke å hente tilgangstoken for PDL"
                logPersonoppslagFailed(
                    errorCode = PdlPersonoppslagErrorCode.PDL_ACCESS_TOKEN_FAILED,
                    retryable = failure.retryable,
                    upstreamStatus =
                        (exception as? AccessTokenRequestFailedException)?.statusCode,
                    message = errorMessage,
                    causeType = exception.javaClass.simpleName,
                )
                throw PdlPersonoppslagFailedException(
                    message = errorMessage,
                    retryable = failure.retryable,
                    cause = exception,
                )
            }

        val pdlResponse =
            try {
                pdlClient.getPerson(fnr = fnr, token = accessToken)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val failure = exception.toPdlRequestFailure() ?: throw exception
                logPersonoppslagFailed(
                    errorCode = failure.errorCode,
                    retryable = failure.retryable,
                    upstreamStatus = (exception as? PdlRequestFailedException)?.statusCode,
                    causeType = exception.javaClass.simpleName,
                )
                throw if (exception is PdlPersonoppslagFailedException) {
                    exception
                } else {
                    PdlPersonoppslagFailedException(
                        message = "Klarte ikke å hente personopplysninger fra PDL",
                        retryable = failure.retryable,
                        cause = exception,
                    )
                }
            }

        val pdlErrors = pdlResponse.errors.orEmpty()
        val person =
            try {
                pdlResponse.toPerson()
            } catch (exception: PdlResponseIncompleteException) {
                val errorCode =
                    if (pdlErrors.isEmpty()) {
                        PdlPersonoppslagErrorCode.PDL_RESPONSE_INCOMPLETE
                    } else {
                        pdlErrors.toPdlPersonoppslagErrorCode()
                    }
                if (pdlErrors.isEmpty()) {
                    logPersonoppslagFailed(
                        errorCode = errorCode,
                        retryable = errorCode.isRetryableGraphQlResponse(),
                        message = "PDL-responsen manglet nødvendige personopplysninger",
                    )
                } else if (errorCode == PdlPersonoppslagErrorCode.PDL_NOT_FOUND) {
                    logPersonoppslagNotFound(pdlErrors)
                } else {
                    logPersonoppslagFailed(
                        pdlErrors = pdlErrors,
                        retryable = errorCode.isRetryableGraphQlResponse(),
                    )
                }
                throw when {
                    // Preserve the existing Kafka skip path for a response without a name.
                    pdlResponse.data != null -> NameNotFoundInPdlException("Fant ikke navn i PDL")
                    pdlErrors.isEmpty() -> exception
                    errorCode == PdlPersonoppslagErrorCode.PDL_NOT_FOUND ->
                        NameNotFoundInPdlException("Fant ikke person i PDL")
                    else ->
                        PdlPersonoppslagFailedException(
                            message = "PDL returnerte feil ved personoppslag",
                            retryable = errorCode.isRetryableGraphQlResponse(),
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

        if (navn == null) {
            throw PdlResponseIncompleteException("Fant ikke navn i PDL-respons")
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

    private fun logPersonoppslagFailed(
        pdlErrors: List<JsonNode>,
        retryable: Boolean,
    ) {
        val errorCode = pdlErrors.toPdlPersonoppslagErrorCode()
        log
            .atError()
            .addKeyValue("event_type", PDL_PERSONOPPSLAG_FAILED)
            .addKeyValue("error_code", errorCode.name)
            .addKeyValue("operation", HENT_PERSONOPPLYSNINGER)
            .addKeyValue("retryable", retryable)
            .addKeyValue("pdl_errors", pdlErrors.toStructuredLogValue())
            .log("Klarte ikke å fullføre personoppslag i PDL")
    }

    private fun logPersonoppslagFailed(
        errorCode: PdlPersonoppslagErrorCode,
        retryable: Boolean,
        upstreamStatus: Int? = null,
        message: String = "Klarte ikke å hente personopplysninger fra PDL",
        causeType: String? = null,
    ) {
        val logBuilder =
            log
                .atError()
                .addKeyValue("event_type", PDL_PERSONOPPSLAG_FAILED)
                .addKeyValue("error_code", errorCode.name)
                .addKeyValue("operation", HENT_PERSONOPPLYSNINGER)
                .addKeyValue("retryable", retryable)
        if (upstreamStatus != null && upstreamStatus in 100..599) {
            logBuilder.addKeyValue("upstream_status", upstreamStatus)
        }
        if (causeType != null) {
            logBuilder.addKeyValue("cause_type", causeType)
        }
        logBuilder.log(message)
    }

    private fun logPersonoppslagNotFound(pdlErrors: List<JsonNode>) {
        log
            .atWarn()
            .addKeyValue("event_type", PDL_PERSONOPPSLAG_NOT_FOUND)
            .addKeyValue("error_code", PdlPersonoppslagErrorCode.PDL_NOT_FOUND.name)
            .addKeyValue("operation", HENT_PERSONOPPLYSNINGER)
            .addKeyValue("pdl_errors", pdlErrors.toStructuredLogValue())
            .log("Fant ikke person i PDL")
    }

    private fun logPartialPersonoppslag(pdlErrors: List<JsonNode>) {
        log
            .atWarn()
            .addKeyValue("event_type", PDL_PERSONOPPSLAG_PARTIAL_RESPONSE)
            .addKeyValue("error_code", pdlErrors.toPdlPersonoppslagErrorCode().name)
            .addKeyValue("operation", HENT_PERSONOPPLYSNINGER)
            .addKeyValue("pdl_errors", pdlErrors.toStructuredLogValue())
            .log("PDL returnerte feil sammen med brukbare personopplysninger")
    }
}

private data class PdlRequestFailure(
    val errorCode: PdlPersonoppslagErrorCode,
    val retryable: Boolean,
)

private fun List<JsonNode>.toStructuredLogValue(): List<Any?> =
    map { objectMapper.convertValue(it, Any::class.java) }

private fun Exception.toPdlRequestFailure(): PdlRequestFailure? =
    when (this) {
        is PdlRequestFailedException ->
            PdlRequestFailure(
                errorCode = PdlPersonoppslagErrorCode.PDL_HTTP_ERROR,
                retryable = retryable,
            )
        is IOException,
        is ServiceUnavailableException,
        ->
            PdlRequestFailure(
                errorCode = PdlPersonoppslagErrorCode.PDL_REQUEST_FAILED,
                retryable = true,
            )
        is ContentConvertException,
        is NoTransformationFoundException,
        ->
            PdlRequestFailure(
                errorCode = PdlPersonoppslagErrorCode.PDL_REQUEST_FAILED,
                retryable = false,
            )
        else -> null
    }

private fun Exception.toAccessTokenFailure(): PdlRequestFailure? =
    when (this) {
        is AccessTokenRequestFailedException ->
            PdlRequestFailure(
                errorCode = PdlPersonoppslagErrorCode.PDL_ACCESS_TOKEN_FAILED,
                retryable = retryable,
            )
        is IOException,
        is ServiceUnavailableException,
        ->
            PdlRequestFailure(
                errorCode = PdlPersonoppslagErrorCode.PDL_ACCESS_TOKEN_FAILED,
                retryable = true,
            )
        is ContentConvertException,
        is NoTransformationFoundException,
        ->
            PdlRequestFailure(
                errorCode = PdlPersonoppslagErrorCode.PDL_ACCESS_TOKEN_FAILED,
                retryable = false,
            )
        else -> null
    }
