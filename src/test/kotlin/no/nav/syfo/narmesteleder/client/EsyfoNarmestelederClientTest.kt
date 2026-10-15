package no.nav.syfo.narmesteleder.client

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.routing
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.texas.client.TexasResponse
import no.nav.syfo.util.httpClientDefault
import org.amshove.kluent.shouldBeEqualTo
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith

class EsyfoNarmestelederClientTest :
    FunSpec({
        val id = UUID.randomUUID().toString()
        val callId = UUID.randomUUID()
        val userToken = "synthetic-user-token"
        val oboToken = "synthetic-obo-token"
        val audience = "local:team-esyfo:esyfo-narmesteleder"
        val texas = mockk<TexasHttpClient>()
        val httpClient = httpClientDefault()
        val requests = AtomicInteger()
        val receivedHeaders = CopyOnWriteArrayList<Map<String, String?>>()
        var status = HttpStatusCode.Accepted
        var responseDelay = 0L
        val port = ServerSocket(0).use { it.localPort }
        val url = "http://localhost:$port"
        val server =
            embeddedServer(Netty, port) {
                routing {
                    delete("/internal/api/v1/linemanager/{id}") {
                        requests.incrementAndGet()
                        call.parameters["id"] shouldBeEqualTo id
                        call.request.local.method shouldBeEqualTo HttpMethod.Delete
                        receivedHeaders.add(
                            listOf("Authorization", "Nav-Call-Id", "Nav-Consumer-Id")
                                .associateWith { call.request.headers[it] },
                        )
                        delay(responseDelay)
                        call.respondText("synthetic-upstream-body", status = status)
                    }
                }
            }.start()
        val client =
            EsyfoNarmestelederClient(
                url,
                audience,
                "dinesykmeldte-backend",
                texas,
                httpClient,
                attemptTimeoutMillis = 200,
                totalTimeoutMillis = 5_000,
                retryBaseDelayMillis = 10,
            )
        val log = LoggerFactory.getLogger(EsyfoNarmestelederClient::class.java) as Logger
        val output = ByteArrayOutputStream()
        val encoder =
            LogstashEncoder().apply {
                context = log.loggerContext
                start()
            }
        val appender =
            OutputStreamAppender<ILoggingEvent>().apply {
                context = log.loggerContext
                this.encoder = encoder
                outputStream = output
                start()
            }
        log.addAppender(appender)

        beforeEach {
            clearMocks(texas)
            coEvery {
                texas.exchangeToken(TexasHttpClient.IDENTITY_PROVIDER_TOKENX, audience, userToken)
            } returns TexasResponse(oboToken, 300, "Bearer")
            requests.set(0)
            receivedHeaders.clear()
            status = HttpStatusCode.Accepted
            responseDelay = 0
            output.reset()
        }

        afterEach {
            val logged = output.toString(Charsets.UTF_8)
            listOf(userToken, oboToken, url, "synthetic-upstream-body").forEach {
                logged.contains(it) shouldBeEqualTo false
            }
        }

        afterSpec {
            httpClient.close()
            server.stop(0, 1000)
            log.detachAppender(appender)
            appender.stop()
            encoder.stop()
        }

        test("202 sends one DELETE with OBO and correlation headers and counts revoked once") {
            val before = metric("revoked")
            client.deaktiverNarmesteLeder(id, userToken, callId)
            requests.get() shouldBeEqualTo 1
            receivedHeaders.single() shouldBeEqualTo
                mapOf(
                    "Authorization" to "Bearer $oboToken",
                    "Nav-Call-Id" to callId.toString(),
                    "Nav-Consumer-Id" to "dinesykmeldte-backend",
                )
            coVerify(exactly = 1) { texas.exchangeToken("tokenx", audience, userToken) }
            metric("revoked") shouldBeEqualTo before + 1
        }

        listOf(
            HttpStatusCode.NotFound,
            HttpStatusCode.BadRequest,
            HttpStatusCode.Forbidden,
            HttpStatusCode.OK,
            HttpStatusCode.Found,
        ).forEach { responseStatus ->
            test("${responseStatus.value} fails without retries and counts once") {
                status = responseStatus
                val result = if (status == HttpStatusCode.NotFound) "not_found" else "failed"
                val before = metric(result)
                val exception =
                    assertFailsWith<EsyfoNarmestelederException> {
                        client.deaktiverNarmesteLeder(id, userToken, callId)
                    }
                assertSafeException(exception, url, userToken, oboToken)
                requests.get() shouldBeEqualTo 1
                metric(result) shouldBeEqualTo before + 1
                if (status == HttpStatusCode.NotFound) {
                    output.toString(Charsets.UTF_8).contains("\"level\":\"WARN\"") shouldBeEqualTo
                        true
                }
            }
        }

        test("500 retries exactly twice, keeps safe logs and counts failed once") {
            status = HttpStatusCode.InternalServerError
            val before = metric("failed")
            val exception =
                assertFailsWith<EsyfoNarmestelederException> {
                    client.deaktiverNarmesteLeder(id, userToken, callId)
                }
            assertSafeException(exception, url, userToken, oboToken)
            requests.get() shouldBeEqualTo 3
            receivedHeaders.forEach {
                it["Authorization"] shouldBeEqualTo "Bearer $oboToken"
            }
            metric("failed") shouldBeEqualTo before + 1
            coVerify(exactly = 1) { texas.exchangeToken("tokenx", audience, userToken) }
        }

        test("real socket timeout overrides shared timeout and retries exactly twice") {
            responseDelay = 1_000
            val before = metric("failed")
            val exception =
                assertFailsWith<EsyfoNarmestelederException> {
                    client.deaktiverNarmesteLeder(id, userToken, callId)
                }
            assertSafeException(exception, url, userToken, oboToken)
            requests.get() shouldBeEqualTo 3
            metric("failed") shouldBeEqualTo before + 1
        }

        test("real total request timeout fails once without retries") {
            responseDelay = 1_000
            val totalTimeoutClient =
                EsyfoNarmestelederClient(
                    url,
                    audience,
                    "dinesykmeldte-backend",
                    texas,
                    httpClient,
                    attemptTimeoutMillis = 5_000,
                    totalTimeoutMillis = 200,
                    retryBaseDelayMillis = 10,
                )
            val before = metric("failed")
            val exception =
                assertFailsWith<EsyfoNarmestelederException> {
                    totalTimeoutClient.deaktiverNarmesteLeder(id, userToken, callId)
                }
            assertSafeException(exception, url, userToken, oboToken)
            requests.get() shouldBeEqualTo 1
            metric("failed") shouldBeEqualTo before + 1
            output
                .toString(Charsets.UTF_8)
                .contains("\"exception\":\"HttpRequestTimeoutException\"") shouldBeEqualTo true
        }

        test("Texas failure is sanitized, counts failed and sends no DELETE") {
            coEvery {
                texas.exchangeToken(any(), any(), any())
            } throws IllegalStateException("synthetic-sensitive-upstream-message")
            val before = metric("failed")
            val exception =
                assertFailsWith<EsyfoNarmestelederException> {
                    client.deaktiverNarmesteLeder(id, userToken, callId)
                }
            assertSafeException(exception, url, userToken, oboToken)
            output
                .toString(Charsets.UTF_8)
                .contains("synthetic-sensitive-upstream-message") shouldBeEqualTo false
            requests.get() shouldBeEqualTo 0
            metric("failed") shouldBeEqualTo before + 1
        }

        test("coroutine cancellation propagates without a DELETE or failure metric") {
            coEvery { texas.exchangeToken(any(), any(), any()) } throws CancellationException()
            val before = metric("failed")
            assertFailsWith<CancellationException> {
                client.deaktiverNarmesteLeder(id, userToken, callId)
            }
            requests.get() shouldBeEqualTo 0
            metric("failed") shouldBeEqualTo before
        }

        listOf(
            HttpRequestTimeoutException("synthetic-url", 1),
            SocketTimeoutException("synthetic-message"),
            ConnectException("synthetic-message"),
            ConnectTimeoutException("synthetic-message", null),
            IllegalStateException("synthetic-wrapper", SocketTimeoutException("synthetic-message")),
            IllegalStateException("synthetic-wrapper", ConnectException("synthetic-message")),
            IllegalStateException(
                "synthetic-wrapper",
                HttpRequestTimeoutException("synthetic-url", 1),
            ),
        ).forEachIndexed { index, cause ->
            test(
                "${cause.javaClass.simpleName} case $index retries twice and sanitizes exception",
            ) {
                val attempts = AtomicInteger()
                val engine =
                    MockEngine {
                        attempts.incrementAndGet()
                        throw cause
                    }
                val failingHttpClient =
                    HttpClient(engine) {
                        install(HttpRequestRetry) { retryOnServerErrors(3) }
                    }
                try {
                    val failingClient =
                        EsyfoNarmestelederClient(
                            url,
                            audience,
                            "dinesykmeldte-backend",
                            texas,
                            failingHttpClient,
                            retryBaseDelayMillis = 1,
                        )
                    val before = metric("failed")
                    val exception =
                        assertFailsWith<EsyfoNarmestelederException> {
                            failingClient.deaktiverNarmesteLeder(id, userToken, callId)
                        }
                    assertSafeException(exception, url, userToken, oboToken)
                    attempts.get() shouldBeEqualTo 3
                    metric("failed") shouldBeEqualTo before + 1
                } finally {
                    failingHttpClient.close()
                }
            }
        }
    })

private fun metric(result: String): Double =
    CollectorRegistry.defaultRegistry.getSampleValue(
        "dinesykmeldte_backend_nl_brudd_esyfo_narmesteleder_counter_total",
        arrayOf("result"),
        arrayOf(result),
    ) ?: 0.0

private fun assertSafeException(
    exception: EsyfoNarmestelederException,
    vararg forbidden: String,
) {
    exception.message shouldBeEqualTo "Kunne ikke deaktivere NL-kobling i esyfo-narmesteleder"
    forbidden.forEach { exception.message.orEmpty().contains(it) shouldBeEqualTo false }
    exception.cause shouldBeEqualTo null
}
