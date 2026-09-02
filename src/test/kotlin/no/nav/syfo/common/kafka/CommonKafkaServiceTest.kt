package no.nav.syfo.common.kafka

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import com.fasterxml.jackson.databind.JsonNode
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.hendelser.HendelserService
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PDL_PERSONOPPSLAG_FAILED
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.soknad.SoknadService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.util.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CommonKafkaServiceTest :
    FunSpec({
        test("wakeup exception avslutter kontrollert og lukker consumer med timeout") {
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            every { kafkaConsumer.poll(any<Duration>()) } throws WakeupException()
            val commonKafkaService = createCommonKafkaService(kafkaConsumer)

            runBlocking {
                commonKafkaService.startConsumer()
            }

            verify(exactly = 1) { kafkaConsumer.close(Duration.ofSeconds(3)) }
            verify(exactly = 0) { kafkaConsumer.close() }
            verify(exactly = 0) { kafkaConsumer.unsubscribe() }
        }

        test("close med timeout brukes også når wakeup shutdown får close-feil") {
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            every { kafkaConsumer.poll(any<Duration>()) } throws WakeupException()
            every {
                kafkaConsumer.close(Duration.ofSeconds(3))
            } throws RuntimeException("close failed")
            val commonKafkaService = createCommonKafkaService(kafkaConsumer)

            runBlocking {
                commonKafkaService.startConsumer()
            }

            verify(exactly = 1) { kafkaConsumer.close(Duration.ofSeconds(3)) }
            verify(exactly = 0) { kafkaConsumer.close() }
            verify(exactly = 0) { kafkaConsumer.unsubscribe() }
        }

        test("cancellation exception propageres og behandles ikke som kafka-feil") {
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            every { kafkaConsumer.poll(any<Duration>()) } throws CancellationException("cancelled")
            val commonKafkaService = createCommonKafkaService(kafkaConsumer)

            assertFailsWith<CancellationException> {
                runBlocking {
                    commonKafkaService.startConsumer()
                }
            }

            verify(exactly = 1) { kafkaConsumer.close(Duration.ofSeconds(3)) }
            verify(exactly = 0) { kafkaConsumer.close() }
            verify(exactly = 0) { kafkaConsumer.unsubscribe() }
        }

        test("reelle kafka-feil beholder retry-path med unsubscribe og retry-delay") {
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            val unsubscribeLatch = CountDownLatch(1)
            every { kafkaConsumer.poll(any<Duration>()) } throws RuntimeException("boom")
            every { kafkaConsumer.unsubscribe() } answers { unsubscribeLatch.countDown() }
            val commonKafkaService = createCommonKafkaService(kafkaConsumer)

            runBlocking {
                val consumerJob = launch(Dispatchers.Default) { commonKafkaService.startConsumer() }

                unsubscribeLatch.await(1, TimeUnit.SECONDS) shouldBeEqualTo true
                delay(100)
                consumerJob.cancel()
                consumerJob.join()
            }

            verify(exactly = 1) { kafkaConsumer.subscribe(any<Collection<String>>()) }
            verify(exactly = 1) { kafkaConsumer.poll(Duration.ofSeconds(10)) }
            verify(exactly = 1) { kafkaConsumer.unsubscribe() }
            verify(exactly = 1) { kafkaConsumer.close(Duration.ofSeconds(3)) }
            verify(exactly = 0) { kafkaConsumer.close() }
        }

        test("serialiserer ikke cause fra allerede logget PDL-feil i kafka-laget") {
            val causeCanary = "PDL_CAUSE_CANARY"
            val fnrCanary = "12345678910"
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            val records =
                ConsumerRecords(
                    mapOf(
                        TopicPartition(sendtSykmeldingTopic, 0) to
                            listOf(
                                ConsumerRecord(
                                    sendtSykmeldingTopic,
                                    0,
                                    0,
                                    "sykmelding-id",
                                    "{}",
                                ),
                            ),
                    ),
                )
            every { kafkaConsumer.poll(any<Duration>()) } returns records
            val unsubscribeLatch = CountDownLatch(1)
            every { kafkaConsumer.unsubscribe() } answers { unsubscribeLatch.countDown() }
            val accessTokenClient = mockk<AccessTokenClient>()
            coEvery { accessTokenClient.getAccessToken(any()) } throws
                IllegalStateException(causeCanary)
            val pdlPersonService =
                PdlPersonService(
                    pdlClient = mockk<PdlClient>(),
                    accessTokenClient = accessTokenClient,
                    pdlScope = "scope",
                )
            val sykmeldingService = mockk<SykmeldingService>()
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(any()) } coAnswers {
                pdlPersonService.getPerson(fnrCanary)
                Unit
            }
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    sykmeldingService = sykmeldingService,
                )

            val logs =
                captureKafkaFlowLogs {
                    runBlocking {
                        val consumerJob =
                            launch(Dispatchers.Default) { commonKafkaService.startConsumer() }

                        unsubscribeLatch.await(1, TimeUnit.SECONDS) shouldBeEqualTo true
                        consumerJob.cancel()
                        consumerJob.join()
                    }
                }

            val serializedLogs = logs.joinToString(separator = "\n", transform = JsonNode::toString)
            assertFalse(serializedLogs.contains(causeCanary))
            assertFalse(serializedLogs.contains(fnrCanary))
            val terminalErrors = logs.filter { it["level"]?.asText() == "ERROR" }
            terminalErrors.size shouldBeEqualTo 1
            terminalErrors.single()["event_type"].asText() shouldBeEqualTo
                PDL_PERSONOPPSLAG_FAILED
            assertFalse(logs.any { it["level"]?.asText() == "WARN" })
            verify(exactly = 1) { kafkaConsumer.unsubscribe() }
            verify(exactly = 1) { kafkaConsumer.close(Duration.ofSeconds(3)) }
        }

        test("kafkaWakeup delegere til underliggende consumer") {
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            val commonKafkaService = createCommonKafkaService(kafkaConsumer)

            commonKafkaService.kafkaWakeup()

            verify(exactly = 1) { kafkaConsumer.wakeup() }
        }
    })

private fun createCommonKafkaService(
    kafkaConsumer: KafkaConsumer<String, String>,
    applicationState: ApplicationState = ApplicationState(),
    sykmeldingService: SykmeldingService = mockk(relaxed = true),
): CommonKafkaService {
    val environment = mockk<Environment>()
    every { environment.narmestelederLeesahTopic } returns
        "teamsykmelding.syfo-narmesteleder-leesah"
    every { environment.syfoNarmestelederLeesahTopic } returns
        "team-esyfo.syfo-narmesteleder-leesah"
    every { environment.sendtSykmeldingTopic } returns "teamsykmelding.syfo-sendt-sykmelding"
    every { environment.sykepengesoknadTopic } returns "flex.sykepengesoknad"
    every { environment.hendelserTopic } returns "team-esyfo.dinesykmeldte-hendelser-v2"
    every { environment.consumeTeamsykmeldingNlLeesahTopic } returns true
    every { environment.consumeTeamEsyfoNlLeesahTopic } returns false

    return CommonKafkaService(
        kafkaConsumer = kafkaConsumer,
        applicationState = applicationState,
        environment = environment,
        narmestelederService = mockk<NarmestelederService>(relaxed = true),
        sykmeldingService = sykmeldingService,
        soknadService = mockk<SoknadService>(relaxed = true),
        hendelserService = mockk<HendelserService>(relaxed = true),
    )
}

private suspend fun captureKafkaFlowLogs(block: suspend () -> Unit): List<JsonNode> {
    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    val outputStream = ByteArrayOutputStream()
    val encoder =
        LogstashEncoder().apply {
            context = loggerContext
            start()
        }
    val appender =
        OutputStreamAppender<ILoggingEvent>().apply {
            context = loggerContext
            name = "kafka-contract-${UUID.randomUUID()}"
            this.encoder = encoder
            setOutputStream(outputStream)
            start()
        }
    val flowLoggers =
        listOf(
            loggerContext.getLogger(CommonKafkaService::class.java),
            loggerContext.getLogger(PdlPersonService::class.java),
        )
    flowLoggers.forEach { it.addAppender(appender) }

    try {
        block()
    } finally {
        flowLoggers.forEach { it.detachAppender(appender) }
        appender.stop()
        encoder.stop()
    }

    return outputStream
        .toString(Charsets.UTF_8)
        .lineSequence()
        .filter(String::isNotBlank)
        .map(objectMapper::readTree)
        .toList()
}
