package no.nav.syfo.pdl.client.model

data class GetPersonResponse(
    val data: ResponseData? = null,
    val errors: List<ResponseError>? = null,
)

data class ResponseData(
    val person: PersonResponse? = null,
    val identer: IdentResponse? = null,
)

data class IdentResponse(
    val identer: List<Ident>,
)

data class Ident(
    val ident: String,
    val gruppe: String,
)

data class PersonResponse(
    val navn: List<Navn>?,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

data class ResponseError(
    val message: String? = null,
    val locations: List<ErrorLocation>? = null,
    val path: List<String>? = null,
    val extensions: ErrorExtension? = null,
)

data class ErrorLocation(
    val line: String?,
    val column: String?,
)

data class ErrorExtension(
    val code: String? = null,
    val details: ErrorDetails? = null,
    val classification: String? = null,
)

data class ErrorDetails(
    val type: String? = null,
    val cause: String? = null,
    val policy: String? = null,
)
