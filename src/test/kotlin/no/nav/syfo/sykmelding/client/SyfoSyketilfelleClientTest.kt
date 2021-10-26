package no.nav.syfo.sykmelding.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.AccessTokenClient
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

object SyfoSyketilfelleClientTest : Spek({
    val sykmeldingUUID = UUID.randomUUID()
    val oppfolgingsdato1 = LocalDate.of(2019, 9, 30)
    val oppfolgingsdato2 = LocalDate.of(2020, 1, 30)
    val oppfolgingsdato3 = LocalDate.of(2018, 10, 15)

    val aktorId1 = "123456"
    val aktorId2 = "654321"

    val accessTokenClient = mockk<AccessTokenClient>()
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
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
            get("/syfosyketilfelle/sparenaproxy/$aktorId1/sykeforloep") {
                call.respond(
                    listOf(
                        Sykeforloep(
                            oppfolgingsdato1,
                            listOf(
                                SimpleSykmelding(
                                    UUID.randomUUID().toString(),
                                    oppfolgingsdato1,
                                    oppfolgingsdato1.plusWeeks(3)
                                )
                            )
                        ),
                        Sykeforloep(
                            oppfolgingsdato2,
                            listOf(
                                SimpleSykmelding(
                                    sykmeldingUUID.toString(),
                                    oppfolgingsdato2,
                                    oppfolgingsdato2.plusWeeks(4)
                                )
                            )
                        ),
                        Sykeforloep(
                            oppfolgingsdato3,
                            listOf(
                                SimpleSykmelding(
                                    UUID.randomUUID().toString(),
                                    oppfolgingsdato3,
                                    oppfolgingsdato3.plusWeeks(8)
                                )
                            )
                        )
                    )
                )
            }
            get("/syfosyketilfelle/sparenaproxy/$aktorId2/sykeforloep") {
                call.respond(
                    listOf(
                        Sykeforloep(
                            oppfolgingsdato1,
                            listOf(
                                SimpleSykmelding(
                                    UUID.randomUUID().toString(),
                                    oppfolgingsdato1,
                                    oppfolgingsdato1.plusWeeks(3)
                                )
                            )
                        ),
                        Sykeforloep(
                            oppfolgingsdato3,
                            listOf(
                                SimpleSykmelding(
                                    UUID.randomUUID().toString(),
                                    oppfolgingsdato3,
                                    oppfolgingsdato3.plusWeeks(8)
                                )
                            )
                        )
                    )
                )
            }
        }
    }.start()

    val syfoSyketilfelleClient = SyfoSyketilfelleClient(
        mockHttpServerUrl,
        accessTokenClient,
        "scope",
        httpClient
    )

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeEachTest {
        coEvery { accessTokenClient.getAccessToken(any()) } returns "token"
    }

    describe("Test av SyfoSyketilfelleClient - finnStartDato") {
        it("Henter riktig startdato fra syfosyketilfelle") {
            runBlocking {
                val startDato = syfoSyketilfelleClient.finnStartdato(aktorId1, sykmeldingUUID.toString())
                startDato shouldBeEqualTo oppfolgingsdato2
            }
        }
        it("Kaster feil hvis sykmelding ikke er knyttet til syketilfelle") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    syfoSyketilfelleClient.finnStartdato(aktorId2, sykmeldingUUID.toString())
                }
            }
        }
    }
})
