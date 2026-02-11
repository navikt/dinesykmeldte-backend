package no.nav.syfo.pdl.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import no.nav.syfo.pdl.client.model.GetPersonRequest
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.GetPersonVariables
import no.nav.syfo.util.logger
import org.intellij.lang.annotations.Language

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
      identer: hentIdenter(ident: ${'$'}ident, historikk: false) {
          identer {
            ident,
            gruppe
          }
        }
    }
    """.trimIndent()

class PdlClient(
    private val httpClient: HttpClient,
    private val basePath: String,
) {
    companion object {
        private val log = logger()
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
            val responseText = response.body<String>()
            log.error("Feil ved kall mot PDL: Status: ${response.status}. Message: $responseText")
            throw RuntimeException("Feil ved kall mot PDL: ${response.status}, $responseText")
        }
    }
}
