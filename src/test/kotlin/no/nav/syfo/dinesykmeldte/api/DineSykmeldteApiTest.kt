package no.nav.syfo.dinesykmeldte.api

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.util.addAuthorizationHeader
import no.nav.syfo.util.objectMapper
import no.nav.syfo.util.withKtor
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

class DineSykmeldteApiTest :
    FunSpec({
        test("missing sykmeldt has a stable reason without exposing the relation or leader") {
            val service = mockk<DineSykmeldteService>()
            val relationId = "81cb7211-b77a-4613-ae77-1e726efaf384"
            val leader = "08086912345"
            coEvery { service.getSykmeldt(relationId, leader) } returns null
            val logger = LoggerFactory.getLogger("no.nav.syfo.dinesykmeldte") as Logger
            val output = ByteArrayOutputStream()
            val encoder =
                LogstashEncoder().apply {
                    context = logger.loggerContext
                    start()
                }
            val appender =
                OutputStreamAppender<ILoggingEvent>().apply {
                    context = logger.loggerContext
                    this.encoder = encoder
                    outputStream = output
                    start()
                }
            logger.addAppender(appender)
            try {
                withKtor({ registerDineSykmeldteApi(service) }) {
                    runBlocking {
                        val response =
                            client.get("/api/v2/dinesykmeldte/$relationId") {
                                addAuthorizationHeader(subject = leader)
                            }
                        response.status shouldBe HttpStatusCode.NotFound
                        val body = objectMapper.readTree(response.bodyAsText())
                        body["error_code"].asText() shouldBe "SYKMELDT_NOT_FOUND"
                        body.size() shouldBe 1
                    }
                }
                coVerify(exactly = 1) { service.getSykmeldt(relationId, leader) }
                val logged = objectMapper.readTree(output.toString(Charsets.UTF_8))
                logged["event_type"].asText() shouldBe "sykmeldt_not_found"
                logged["error_code"].asText() shouldBe "SYKMELDT_NOT_FOUND"
                output.toString(Charsets.UTF_8).contains(relationId) shouldBe false
                output.toString(Charsets.UTF_8).contains(leader) shouldBe false
            } finally {
                logger.detachAppender(appender)
                appender.stop()
                encoder.stop()
            }
        }

        test("an unknown route does not claim a missing sykmeldt") {
            val service = mockk<DineSykmeldteService>()
            withKtor({ registerDineSykmeldteApi(service) }) {
                runBlocking {
                    val response = client.get("/api/v2/unknown") { addAuthorizationHeader() }
                    response.status shouldBe HttpStatusCode.NotFound
                    response.bodyAsText().contains("SYKMELDT_NOT_FOUND") shouldBe false
                }
            }
            coVerify(exactly = 0) { service.getSykmeldt(any(), any()) }
        }

        test("unauthenticated requests cannot read the relation") {
            val service = mockk<DineSykmeldteService>()
            withKtor({ registerDineSykmeldteApi(service) }) {
                runBlocking {
                    client.get("/api/v2/dinesykmeldte/some-relation").status shouldBe
                        HttpStatusCode.Unauthorized
                }
            }
            coVerify(exactly = 0) { service.getSykmeldt(any(), any()) }
        }
    })
