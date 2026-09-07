package no.nav.syfo.pdl.service

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.serialization.ContentConvertException
import no.nav.syfo.azuread.AccessTokenRequestFailedException
import no.nav.syfo.common.exception.ServiceUnavailableException
import no.nav.syfo.pdl.exceptions.PdlRequestFailedException
import java.io.IOException

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

internal data class PdlRequestFailure(
    val errorCode: PdlPersonoppslagErrorCode,
    val retryable: Boolean,
)

internal fun Exception.toPdlRequestFailure(): PdlRequestFailure? =
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

internal fun Exception.toAccessTokenFailure(): PdlRequestFailure? =
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
