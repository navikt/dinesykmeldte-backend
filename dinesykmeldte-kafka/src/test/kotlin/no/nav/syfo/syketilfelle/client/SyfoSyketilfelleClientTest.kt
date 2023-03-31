package no.nav.syfo.syketilfelle.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.log
import org.amshove.kluent.shouldBeEqualTo
import java.net.ServerSocket
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class SyfoSyketilfelleClientTest : FunSpec({
    val sykmeldingUUID = UUID.randomUUID()
    val oppfolgingsdato1 = LocalDate.of(2019, 9, 30)
    val oppfolgingsdato2 = LocalDate.of(2020, 1, 30)
    val oppfolgingsdato3 = LocalDate.of(2018, 10, 15)

    val fnr1 = "123456"
    val fnr2 = "654321"
    val fnr3 = "1234567"
    val accessTokenClient = mockk<AccessTokenClient>()
    val httpClient = HttpClient(Apache) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val socketTimeoutClient = HttpClient(Apache) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 1L
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn("Retrying for statuscode ${response.status.value}, for url ${request.url}")
                    true
                } else {
                    false
                }
            }
        }
    }

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        routing {
            get("/api/v1/sykeforloep") {
                when (call.request.headers["fnr"]) {
                    fnr3 -> {
                        delay(10_000)
                        call.respond(emptyList<Sykeforloep>())
                    }
                    fnr1 -> call.respond(
                        listOf(
                            Sykeforloep(
                                oppfolgingsdato1,
                                listOf(
                                    SimpleSykmelding(
                                        UUID.randomUUID().toString(),
                                        oppfolgingsdato1,
                                        oppfolgingsdato1.plusWeeks(3),
                                    ),
                                ),
                            ),
                            Sykeforloep(
                                oppfolgingsdato2,
                                listOf(
                                    SimpleSykmelding(
                                        sykmeldingUUID.toString(),
                                        oppfolgingsdato2,
                                        oppfolgingsdato2.plusWeeks(4),
                                    ),
                                ),
                            ),
                            Sykeforloep(
                                oppfolgingsdato3,
                                listOf(
                                    SimpleSykmelding(
                                        UUID.randomUUID().toString(),
                                        oppfolgingsdato3,
                                        oppfolgingsdato3.plusWeeks(8),
                                    ),
                                ),
                            ),
                        ),
                    )
                    fnr2 -> call.respond(
                        listOf(
                            Sykeforloep(
                                oppfolgingsdato1,
                                listOf(
                                    SimpleSykmelding(
                                        UUID.randomUUID().toString(),
                                        oppfolgingsdato1,
                                        oppfolgingsdato1.plusWeeks(3),
                                    ),
                                ),
                            ),
                            Sykeforloep(
                                oppfolgingsdato3,
                                listOf(
                                    SimpleSykmelding(
                                        UUID.randomUUID().toString(),
                                        oppfolgingsdato3,
                                        oppfolgingsdato3.plusWeeks(8),
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            }
        }
    }.start()

    val syfoSyketilfelleClient = SyfoSyketilfelleClient(
        mockHttpServerUrl,
        accessTokenClient,
        "scope",
        httpClient,
    )

    val syfoSyketilfelletSocketTimeoutClient = SyfoSyketilfelleClient(
        mockHttpServerUrl,
        accessTokenClient,
        "scope",
        socketTimeoutClient,
    )

    afterSpec {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeEach {
        coEvery { accessTokenClient.getAccessToken(any()) } returns "token"
    }

    context("Test av SyfoSyketilfelleClient - finnStartDato") {
        test("Should get SocketTimeoutException") {
            assertFailsWith<SocketTimeoutException> {
                syfoSyketilfelletSocketTimeoutClient.finnStartdato(fnr3, sykmeldingUUID.toString())
            }
        }

        test("Henter riktig startdato fra syfosyketilfelle") {
            val startDato = syfoSyketilfelleClient.finnStartdato(fnr1, sykmeldingUUID.toString())
            startDato shouldBeEqualTo oppfolgingsdato2
        }
        test("Kaster feil hvis sykmelding ikke er knyttet til syketilfelle") {
            assertFailsWith<SyketilfelleNotFoundException> {
                runBlocking {
                    syfoSyketilfelleClient.finnStartdato(fnr2, sykmeldingUUID.toString())
                }
            }
        }
    }
})
