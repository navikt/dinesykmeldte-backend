package no.nav.syfo.minesykmeldte.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.setupAuth
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.model.PreviewSykmeldt
import no.nav.syfo.objectMapper
import no.nav.syfo.testutils.generateJWTLoginservice
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths
import java.time.LocalDate

object MineSykmeldteApiKtTest : Spek({
    val mineSykmeldteService = mockk<MineSykmeldteService>()
    val env = mockk<Environment>()

    afterEachTest {
        clearMocks(mineSykmeldteService, env)
    }

    with(TestApplicationEngine()) {
        start()
        val applicationState = ApplicationState()
        applicationState.ready = true
        applicationState.alive = true
        application.install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        application.setupAuth(
            jwkProviderTokenX = JwkProviderBuilder(Paths.get("src/test/resources/jwkset.json").toUri().toURL()).build(),
            tokenXIssuer = "https://sts.issuer.net/myid",
            env = env
        )
        application.routing {
            authenticate("tokenx") {
                registerMineSykmeldteApi(mineSykmeldteService)
            }
        }

        describe("MineSykmeldteApi test") {
            it("should get empty list") {
                every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWTLoginservice(
                                audience = "dummy-client-id",
                                subject = "08086912345",
                                issuer = "https://sts.issuer.net/myid"
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.content shouldBeEqualTo "[]"
                }
            }

            it("should get data in list") {
                val startdato = LocalDate.now().minusDays(14)
                every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns listOf(
                    PreviewSykmeldt(
                        narmestelederId = "08086912345",
                        orgnummer = "orgnummer",
                        fnr = "fnr",
                        navn = "navn",
                        startdatoSykefravaer = LocalDate.now().minusDays(14),
                        friskmeldt = false,
                        previewSykmeldinger = emptyList(),
                        previewSoknader = emptyList(),
                    )
                )
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWTLoginservice(
                                audience = "dummy-client-id",
                                subject = "08086912345",
                                issuer = "https://sts.issuer.net/myid"
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.content shouldBeEqualTo """[ {
                          "narmestelederId": "08086912345",
                          "orgnummer": "orgnummer",
                          "fnr": "fnr",
                          "navn": "navn",
                          "startdatoSykefravaer": "$startdato",
                          "friskmeldt": false,
                          "previewSykmeldinger": [],
                          "previewSoknader": []
                        }
                    ]""".minifyApiResponse()
                }
            }

            it("should return 401 when missing a valid bearer token") {
                every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addHeader(
                            "Authorization",
                            "Bearer tull"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("should return 401 when providing the wrong audience") {
                every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWTLoginservice(
                                audience = "wrong-dummy-client-id",
                                subject = "08086912345",
                                issuer = "https://sts.issuer.net/myid"
                            )
                            }"

                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("should return 401 when providing the wrong issuer") {
                every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWTLoginservice(
                                audience = "dummy-client-id",
                                subject = "08086912345",
                                issuer = "https://wrong.issuer.net/myid"
                            )
                            }"

                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("should return 401 when jwt has the wrong access level") {
                every { env.dineSykmeldteBackendTokenXClientId } returns "dummy-client-id"
                every { mineSykmeldteService.getMineSykmeldte("08086912345") } returns emptyList()
                with(
                    handleRequest(HttpMethod.Get, "/api/minesykmeldte") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWTLoginservice(
                                audience = "dummy-client-id",
                                subject = "08086912345",
                                issuer = "https://sts.issuer.net/myid",
                                level = "Level3",
                            )
                            }"

                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

private fun String.minifyApiResponse(): String =
    objectMapper.writeValueAsString(
        objectMapper.readValue<List<PreviewSykmeldt>>(this)
    )
