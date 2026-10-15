package no.nav.syfo.narmesteleder.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import no.nav.syfo.application.metrics.NL_BRUDD_ESYFO_NARMESTELEDER_COUNTER
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.logger
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.UUID

class EsyfoNarmestelederClient(
    private val url: String,
    private val audience: String,
    private val applicationName: String,
    private val texasClient: TexasHttpClient,
    private val httpClient: HttpClient,
    private val attemptTimeoutMillis: Long = 5_000,
    private val totalTimeoutMillis: Long = 20_000,
    private val retryBaseDelayMillis: Long = 1_000,
) {
    private val log = logger()

    suspend fun deaktiverNarmesteLeder(
        narmestelederId: String,
        userToken: String,
        callId: UUID,
    ) {
        val response =
            try {
                val token =
                    texasClient.exchangeToken(
                        identityProvider = TexasHttpClient.IDENTITY_PROVIDER_TOKENX,
                        target = audience,
                        token = userToken,
                    )
                httpClient.delete(
                    "${url.trimEnd('/')}/internal/api/v1/linemanager/$narmestelederId",
                ) {
                    expectSuccess = false
                    bearerAuth(token.accessToken)
                    header("Nav-Call-Id", callId.toString())
                    header("Nav-Consumer-Id", applicationName)
                    timeout {
                        // HttpTimeout precedes HttpRequestRetry: request timeout is not retryable.
                        // Use socket/connect timeouts to bound each attempt and retry it.
                        connectTimeoutMillis = attemptTimeoutMillis
                        socketTimeoutMillis = attemptTimeoutMillis
                        requestTimeoutMillis = totalTimeoutMillis
                    }
                    retry {
                        retryOnServerErrors(maxRetries = 2)
                        retryOnExceptionIf(maxRetries = 2) { _, cause ->
                            cause.isRetryable()
                        }
                        exponentialDelay(
                            baseDelayMs = retryBaseDelayMillis,
                            randomizationMs = 0,
                            respectRetryAfterHeader = false,
                        )
                        modifyRequest {
                            val builder =
                                log
                                    .atWarn()
                                    .addKeyValue("narmestelederId", narmestelederId)
                                    .addKeyValue("callId", callId)
                                    .addKeyValue("attempt", retryCount)
                            response?.let { builder.addKeyValue("status", it.status.value) }
                            cause?.let { builder.addKeyValue("exception", it.javaClass.simpleName) }
                            builder.log("Prøver deaktivering av NL-kobling på nytt")
                        }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                NL_BRUDD_ESYFO_NARMESTELEDER_COUNTER.labels("failed").inc()
                log
                    .atError()
                    .addKeyValue("narmestelederId", narmestelederId)
                    .addKeyValue("callId", callId)
                    .addKeyValue("result", "failed")
                    .addKeyValue("exception", exception.javaClass.simpleName)
                    .log("Deaktivering av NL-kobling i esyfo-narmesteleder feilet")
                // Do not attach an upstream exception: StatusPages logs the entire cause chain.
                throw EsyfoNarmestelederException()
            }

        when (response.status) {
            HttpStatusCode.Accepted -> {
                NL_BRUDD_ESYFO_NARMESTELEDER_COUNTER.labels("revoked").inc()
                log
                    .atInfo()
                    .addKeyValue("narmestelederId", narmestelederId)
                    .addKeyValue("callId", callId)
                    .addKeyValue("result", "revoked")
                    .addKeyValue("status", response.status.value)
                    .log("NL-kobling deaktivert i esyfo-narmesteleder")
            }

            HttpStatusCode.NotFound -> {
                NL_BRUDD_ESYFO_NARMESTELEDER_COUNTER.labels("not_found").inc()
                log
                    .atWarn()
                    .addKeyValue("narmestelederId", narmestelederId)
                    .addKeyValue("callId", callId)
                    .log("Fant ikke tilgjengelig NL-kobling i esyfo-narmesteleder")
                throw EsyfoNarmestelederException()
            }

            else -> {
                NL_BRUDD_ESYFO_NARMESTELEDER_COUNTER.labels("failed").inc()
                log
                    .atError()
                    .addKeyValue("narmestelederId", narmestelederId)
                    .addKeyValue("callId", callId)
                    .addKeyValue("result", "failed")
                    .addKeyValue("status", response.status.value)
                    .log("Deaktivering av NL-kobling i esyfo-narmesteleder feilet")
                throw EsyfoNarmestelederException()
            }
        }
    }
}

private fun Throwable.isRetryable(): Boolean =
    this !is CancellationException &&
        (
            this is HttpRequestTimeoutException ||
                this is SocketTimeoutException ||
                this is ConnectException ||
                cause?.isRetryable() == true
        )
