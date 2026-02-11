package no.nav.syfo.util

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String,
)

fun getWellKnownTokenX(
    httpClient: HttpClient,
    wellKnownUrl: String,
) = runBlocking {
    httpClient.get(wellKnownUrl).body<WellKnownTokenX>()
}

class AuthConfiguration(
    val jwkProviderTokenX: JwkProvider,
    val tokenXIssuer: String,
    val clientIdTokenX: String,
)
