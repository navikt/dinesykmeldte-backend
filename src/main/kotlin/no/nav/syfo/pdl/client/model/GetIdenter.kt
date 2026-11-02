package no.nav.syfo.pdl.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

data class GetIdenterRequest(
    val query: String,
    val variables: GetIdenterVariables,
)

data class GetIdenterVariables(
    val identer: List<String>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GetIdenterResponse(
    val data: IdenterData? = null,
    val errors: List<JsonNode>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdenterData(
    val hentIdenterBolk: List<IdenterBolk?>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdenterBolk(
    val ident: String? = null,
    val identer: List<PdlIdent?>? = null,
    val code: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlIdent(
    val ident: String? = null,
    val historisk: Boolean? = null,
    val gruppe: String? = null,
)

enum class IdenterFailureReason(
    val metricValue: String,
) {
    HTTP_ERROR("pdl_http_error"),
    TIMEOUT("pdl_timeout"),
    IO_ERROR("pdl_io_error"),
    GRAPHQL_ERROR("pdl_graphql_error"),
    BAD_REQUEST("pdl_bad_request"),
    INCOMPLETE("pdl_incomplete"),
    ACCESS_TOKEN_FAILED("access_token_failed"),
}

sealed interface IdenterResult {
    class Complete(
        val identer: Set<String>,
        val outcome: String,
    ) : IdenterResult

    class Failure(
        val reason: IdenterFailureReason,
    ) : IdenterResult
}
