package no.nav.syfo.pdl.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import no.nav.syfo.pdl.client.model.GetIdenterRequest
import no.nav.syfo.pdl.client.model.GetIdenterResponse
import no.nav.syfo.pdl.client.model.GetIdenterVariables
import no.nav.syfo.pdl.client.model.GetPersonRequest
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.GetPersonVariables
import no.nav.syfo.pdl.client.model.IdenterFailureReason
import no.nav.syfo.pdl.client.model.IdenterResult
import no.nav.syfo.pdl.exceptions.PdlRequestFailedException
import org.intellij.lang.annotations.Language
import java.io.IOException

// Keep requests below PDL's maximum of 1000 identer per bolk.
internal const val IDENTER_BATCH_SIZE = 100
internal const val IDENTER_LOOKUP_TIMEOUT_MILLIS = 5_000L

@Language("GraphQL")
private val getIdenterQuery =
    """
    query(${'$'}identer: [ID!]!) {
      hentIdenterBolk(identer: ${'$'}identer, grupper: [FOLKEREGISTERIDENT], historikk: true) {
        ident
        identer { ident historisk gruppe }
        code
      }
    }
    """.trimIndent()

@Language("GraphQL")
private val getPersonQuery =
    """
    query(${'$'}ident: ID!){
      person: hentPerson(ident: ${'$'}ident) {
        navn(historikk: false) {
          fornavn
          mellomnavn
          etternavn
        }
      }
    }
    """.trimIndent()

class PdlClient(
    private val httpClient: HttpClient,
    private val basePath: String,
) {
    suspend fun hentIdenter(
        identer: Collection<String>,
        token: String,
        timeoutMillis: Long = IDENTER_LOOKUP_TIMEOUT_MILLIS,
    ): Map<String, IdenterResult> {
        val requested = identer.distinct()
        val results = mutableMapOf<String, IdenterResult>()
        // Keep completed chunks outside the timeout scope so they survive its cancellation.
        withTimeoutOrNull(timeoutMillis) {
            requested.chunked(IDENTER_BATCH_SIZE).forEach { batch ->
                results.putAll(hentIdenterBatch(batch, token))
            }
        }
        return requested.associateWith { ident ->
            results[ident] ?: IdenterResult.Failure(IdenterFailureReason.TIMEOUT)
        }
    }

    private suspend fun hentIdenterBatch(
        identer: List<String>,
        token: String,
    ): Map<String, IdenterResult> {
        try {
            val response =
                httpClient.post(basePath) {
                    retry { noRetry() }
                    timeout {
                        requestTimeoutMillis = 2_000
                        connectTimeoutMillis = 1_000
                        socketTimeoutMillis = 2_000
                    }
                    setBody(GetIdenterRequest(getIdenterQuery, GetIdenterVariables(identer)))
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("TEMA", "SYM")
                    header("Behandlingsnummer", "B229")
                    header(HttpHeaders.ContentType, "application/json")
                }
            if (!response.status.isSuccess()) {
                response.bodyAsChannel().cancel(null)
                return identer.associateWith {
                    IdenterResult.Failure(
                        IdenterFailureReason.HTTP_ERROR,
                    )
                }
            }
            return response.body<GetIdenterResponse>().toIdenterResults(identer)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Never retain or log causes: HTTP/JSON exceptions can contain the raw bolk body.
            val reason =
                when (exception) {
                    is HttpRequestTimeoutException,
                    is ConnectTimeoutException,
                    is SocketTimeoutException,
                    is java.net.SocketTimeoutException,
                    -> IdenterFailureReason.TIMEOUT
                    is IOException -> IdenterFailureReason.IO_ERROR
                    else -> IdenterFailureReason.INCOMPLETE
                }
            return identer.associateWith { IdenterResult.Failure(reason) }
        }
    }

    suspend fun getPerson(
        fnr: String,
        token: String,
    ): GetPersonResponse {
        val getPersonRequest =
            GetPersonRequest(
                query = getPersonQuery,
                variables = GetPersonVariables(ident = fnr),
            )

        val response =
            httpClient
                .post(basePath) {
                    setBody(getPersonRequest)
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("TEMA", "SYM")
                    header("Behandlingsnummer", "B229")
                    header(HttpHeaders.ContentType, "application/json")
                }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            response.bodyAsChannel().cancel(null)
            throw PdlRequestFailedException(statusCode = response.status.value)
        }
    }
}

private fun GetIdenterResponse.toIdenterResults(
    requested: List<String>,
): Map<String, IdenterResult> {
    val elements =
        data
            ?.hentIdenterBolk
            .orEmpty()
            .filterNotNull()
            .groupBy { it.ident }
    return requested.associateWith { ident ->
        val element = elements[ident]?.singleOrNull()
        when (element?.code) {
            "not_found" -> IdenterResult.Complete(setOf(ident), "not_found")
            "bad_request" -> IdenterResult.Failure(IdenterFailureReason.BAD_REQUEST)
            "ok" -> {
                // PDL returns null when the person is not found or the ident is invalid.
                // Only the explicit not_found code above is a cacheable singleton.
                val entries = element.identer
                val folkeregisteridenter =
                    entries.orEmpty().filterNotNull().filter {
                        it.gruppe ==
                            "FOLKEREGISTERIDENT"
                    }
                val resolved = folkeregisteridenter.mapNotNull { it.ident }.toSet()
                if (
                    entries != null &&
                    folkeregisteridenter.count { it.historisk == false } == 1 &&
                    folkeregisteridenter.all { it.ident != null && it.historisk != null } &&
                    ident in resolved
                ) {
                    IdenterResult.Complete(resolved, "found")
                } else {
                    IdenterResult.Failure(IdenterFailureReason.INCOMPLETE)
                }
            }
            else ->
                IdenterResult.Failure(
                    if (!errors.isNullOrEmpty()) {
                        IdenterFailureReason.GRAPHQL_ERROR
                    } else {
                        IdenterFailureReason.INCOMPLETE
                    },
                )
        }
    }
}
