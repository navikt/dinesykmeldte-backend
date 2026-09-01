package no.nav.syfo.pdl.client.model

import com.fasterxml.jackson.databind.JsonNode

data class GetPersonResponse(
    val data: ResponseData? = null,
    val errors: List<JsonNode>? = null,
)

data class ResponseData(
    val person: PersonResponse? = null,
)

data class PersonResponse(
    val navn: List<Navn>?,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)
