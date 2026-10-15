package no.nav.syfo.texas.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.syfo.texas.TexasEnvironment

class TexasHttpClient(
    val client: HttpClient,
    val environment: TexasEnvironment,
) {
    suspend fun introspectToken(
        identityProvider: String,
        token: String,
    ): TexasIntrospectionResponse =
        client
            .post(environment.tokenIntrospectionEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(
                    TexasIntrospectionRequest(
                        identityProvider = identityProvider,
                        token = token,
                    ),
                )
            }.body<TexasIntrospectionResponse>()

    suspend fun systemToken(
        identityProvider: String,
        target: String,
    ): TexasResponse =
        client
            .post(environment.tokenEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(
                    TexasTokenRequest(
                        identityProvider = identityProvider,
                        target = target,
                    ),
                )
            }.body<TexasResponse>()

    suspend fun exchangeToken(
        identityProvider: String,
        target: String,
        token: String,
    ): TexasResponse {
        val response =
            client.post(environment.tokenExchangeEndpoint) {
                expectSuccess = false
                contentType(ContentType.Application.Json)
                setBody(
                    TexasExchangeRequest(
                        identityProvider = identityProvider,
                        target = target,
                        userToken = token,
                    ),
                )
            }
        check(response.status.isSuccess()) { "Tokenutveksling via Texas feilet" }
        return response.body<TexasResponse>()
    }

    companion object {
        fun getTarget(scope: String) = "api://$scope/.default"

        const val IDENTITY_PROVIDER_TOKENX = "tokenx"
        const val IDENTITY_PROVIDER_AZUREAD = "azuread"
    }
}
