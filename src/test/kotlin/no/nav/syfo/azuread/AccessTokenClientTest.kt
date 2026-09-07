package no.nav.syfo.azuread

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode
import no.nav.syfo.util.HttpClientTest
import no.nav.syfo.util.ResponseData
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessTokenClientTest :
    FunSpec({
        test("bevarer status og retry-policy uten å inkludere responsinnhold") {
            val httpClient = HttpClientTest()
            val responseCanary = "ACCESS_TOKEN_RESPONSE_CANARY"
            httpClient.respond {
                ResponseData(
                    httpStatusCode = HttpStatusCode.Unauthorized,
                    content = """{"error":"$responseCanary"}""",
                )
            }
            val client =
                AccessTokenClient(
                    aadAccessTokenUrl = "${httpClient.mockHttpServerUrl}/token",
                    clientId = "client-id",
                    clientSecret = "client-secret",
                    httpClient = httpClient.httpClient,
                )

            val exception =
                assertFailsWith<AccessTokenRequestFailedException> {
                    client.getAccessToken("scope")
                }

            assertEquals(401, exception.statusCode)
            assertFalse(exception.retryable)
            assertFalse(exception.message.orEmpty().contains(responseCanary))
        }

        test("retryer bare transiente token-endepunktstatuser") {
            listOf(408, 425, 429, 500, 503, 599).forEach { statusCode ->
                assertTrue(AccessTokenRequestFailedException(statusCode).retryable)
            }
            listOf(400, 401, 403, 404, 422, 600).forEach { statusCode ->
                assertFalse(AccessTokenRequestFailedException(statusCode).retryable)
            }
        }
    })
