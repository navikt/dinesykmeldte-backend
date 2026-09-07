package no.nav.syfo.pdl.service

import com.fasterxml.jackson.databind.JsonNode
import no.nav.syfo.util.objectMapper
import org.slf4j.Logger

internal const val PDL_PERSONOPPSLAG_FAILED = "pdl_personoppslag_failed"
internal const val PDL_PERSONOPPSLAG_PARTIAL_RESPONSE = "pdl_personoppslag_partial_response"
internal const val PDL_PERSONOPPSLAG_NOT_FOUND = "pdl_personoppslag_not_found"
internal const val HENT_PERSONOPPLYSNINGER = "hent_personopplysninger"

internal class PdlPersonoppslagLogger(
    private val log: Logger,
) {
    fun failed(
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

    fun failed(
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

    fun notFound(pdlErrors: List<JsonNode>) {
        log
            .atWarn()
            .addKeyValue("event_type", PDL_PERSONOPPSLAG_NOT_FOUND)
            .addKeyValue("error_code", PdlPersonoppslagErrorCode.PDL_NOT_FOUND.name)
            .addKeyValue("operation", HENT_PERSONOPPLYSNINGER)
            .addKeyValue("pdl_errors", pdlErrors.toStructuredLogValue())
            .log("Fant ikke person i PDL")
    }

    fun partialResponse(pdlErrors: List<JsonNode>) {
        log
            .atWarn()
            .addKeyValue("event_type", PDL_PERSONOPPSLAG_PARTIAL_RESPONSE)
            .addKeyValue("error_code", pdlErrors.toPdlPersonoppslagErrorCode().name)
            .addKeyValue("operation", HENT_PERSONOPPLYSNINGER)
            .addKeyValue("pdl_errors", pdlErrors.toStructuredLogValue())
            .log("PDL returnerte feil sammen med brukbare personopplysninger")
    }
}

private fun List<JsonNode>.toStructuredLogValue(): List<Any?> =
    map { objectMapper.convertValue(it, Any::class.java) }
