package no.nav.syfo.azuread

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.syfo.util.logger
import java.time.Instant

class AccessTokenClient(
    private val aadAccessTokenUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val httpClient: HttpClient,
) {
    private val mutex = Mutex()
    private val log = logger()

    @Volatile private var tokenMap = HashMap<String, AadAccessTokenMedExpiry>()

    suspend fun getAccessToken(scope: String): String {
        val omToMinutter = Instant.now().plusSeconds(120L)
        return mutex.withLock {
            (
                tokenMap[scope]?.takeUnless { it.expiresOn.isBefore(omToMinutter) }
                    ?: run {
                        log.debug("Henter nytt token fra Azure AD")
                        val response =
                            httpClient
                                .post(aadAccessTokenUrl) {
                                    accept(ContentType.Application.Json)
                                    method = HttpMethod.Post
                                    setBody(
                                        FormDataContent(
                                            Parameters.build {
                                                append("client_id", clientId)
                                                append("scope", scope)
                                                append("grant_type", "client_credentials")
                                                append("client_secret", clientSecret)
                                            },
                                        ),
                                    )
                                }
                        if (!response.status.isSuccess()) {
                            response.bodyAsChannel().cancel(null)
                            throw AccessTokenRequestFailedException(response.status.value)
                        }
                        val accessTokenResponse: AadAccessTokenV2 = response.body()
                        val expiresOn =
                            Instant.now().plusSeconds(accessTokenResponse.expires_in.toLong())
                        val tokenMedExpiry =
                            AadAccessTokenMedExpiry(
                                access_token = accessTokenResponse.access_token,
                                expires_in = accessTokenResponse.expires_in,
                                expiresOn = expiresOn,
                            )
                        tokenMap[scope] = tokenMedExpiry
                        log.debug("Har hentet accesstoken")
                        return@run tokenMedExpiry
                    }
            ).access_token
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessTokenV2(
    val access_token: String,
    val expires_in: Int,
)

data class AadAccessTokenMedExpiry(
    val access_token: String,
    val expires_in: Int,
    val expiresOn: Instant,
)

class AccessTokenRequestFailedException(
    val statusCode: Int,
) : RuntimeException("Azure AD svarte med HTTP-status $statusCode") {
    val retryable: Boolean =
        statusCode == 408 ||
            statusCode == 425 ||
            statusCode == 429 ||
            statusCode in 500..599
}
