package no.nav.syfo.narmesteleder.api

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.client.EsyfoNarmestelederException
import no.nav.syfo.plugins.configureNaisResources
import no.nav.syfo.testutils.generateJWTLoginservice
import no.nav.syfo.util.withKtor
import org.amshove.kluent.shouldBeEqualTo
import java.util.UUID

class NarmestelederApiTest :
    FunSpec({
        val leaderFnr = "synthetic-leader-fnr"
        val relationId = UUID.randomUUID().toString()

        listOf(false, true).forEach { fails ->
            test(
                if (fails) {
                    "revocation failure returns 500 without the leader identifier or token"
                } else {
                    "revocation forwards the authenticated leader, path identifier and raw token"
                },
            ) {
                val service = mockk<NarmestelederService>()
                val userToken =
                    generateJWTLoginservice(
                        audience = "dummy-client-id",
                        subject = leaderFnr,
                        issuer = "https://sts.issuer.net/myid",
                    )
                coEvery {
                    service.deaktiverNarmesteLeder(leaderFnr, relationId, any(), userToken)
                } coAnswers {
                    if (fails) throw EsyfoNarmestelederException()
                }

                withKtor({ registerNarmestelederApi(service) }) {
                    application { configureNaisResources(ApplicationState()) }
                    runBlocking {
                        val response =
                            client.post("/api/narmesteleder/$relationId/avkreft") {
                                bearerAuth(userToken)
                            }
                        response.status shouldBeEqualTo
                            if (fails) HttpStatusCode.InternalServerError else HttpStatusCode.OK
                        val body = response.bodyAsText()
                        if (fails) {
                            body shouldBeEqualTo EsyfoNarmestelederException().message
                        } else {
                            body.contains("Kobling deaktivert") shouldBeEqualTo true
                        }
                        body.contains(leaderFnr) shouldBeEqualTo false
                        body.contains(userToken) shouldBeEqualTo false
                    }
                }
                coVerify(exactly = 1) {
                    service.deaktiverNarmesteLeder(leaderFnr, relationId, any(), userToken)
                }
            }
        }

        test("unauthenticated requests cannot revoke a relation") {
            val service = mockk<NarmestelederService>()
            withKtor({ registerNarmestelederApi(service) }) {
                runBlocking {
                    client.post("/api/narmesteleder/$relationId/avkreft").status shouldBeEqualTo
                        HttpStatusCode.Unauthorized
                }
            }
            coVerify(exactly = 0) {
                service.deaktiverNarmesteLeder(any(), any(), any(), any())
            }
        }
    })
