package no.nav.syfo.common.kafka

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import com.fasterxml.jackson.databind.JsonNode
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.KAFKA_PDL_QUARANTINED_PARTITIONS
import no.nav.syfo.application.metrics.KAFKA_PDL_TERMINAL_FAILURE_COUNTER
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.hendelser.HendelserService
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.exceptions.PdlPersonoppslagFailedException
import no.nav.syfo.pdl.service.PDL_PERSONOPPSLAG_FAILED
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.soknad.SoknadService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.util.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            verifyOrder {
                kafkaConsumer.unsubscribe()
                kafkaConsumer.close(Duration.ofSeconds(3))
            }
        }

        test("cancellation under recordbehandling fjerner assignment før close") {
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            val record = ConsumerRecord(sendtSykmeldingTopic, 0, 42, "sykmelding-id", "{}")
            every { kafkaConsumer.poll(any<Duration>()) } returns
                ConsumerRecords(
                    mapOf(TopicPartition(sendtSykmeldingTopic, 0) to listOf(record)),
                )
            val sykmeldingService = mockk<SykmeldingService>()
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(record) } throws
                CancellationException("shutdown")
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    sykmeldingService = sykmeldingService,
                )

            assertFailsWith<CancellationException> {
                runBlocking { commonKafkaService.startConsumer() }
            }

            coVerifyOrder {
                kafkaConsumer.subscribe(
                    any<Collection<String>>(),
                    any<ConsumerRebalanceListener>(),
                )
                kafkaConsumer.poll(Duration.ofSeconds(10))
                sykmeldingService.handleSendtSykmeldingKafkaMessage(record)
                kafkaConsumer.unsubscribe()
                kafkaConsumer.close(Duration.ofSeconds(3))
            }
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

            verify(exactly = 1) {
                kafkaConsumer.subscribe(
                    any<Collection<String>>(),
                    any<ConsumerRebalanceListener>(),
                )
            }
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
            every { kafkaConsumer.poll(any<Duration>()) } returns records andThenThrows
                WakeupException()
            val accessTokenClient = mockk<AccessTokenClient>()
            coEvery { accessTokenClient.getAccessToken(any()) } throws
                IOException(causeCanary)
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
                    commonKafkaService.startConsumer()
                }

            val serializedLogs = logs.joinToString(separator = "\n", transform = JsonNode::toString)
            assertFalse(serializedLogs.contains(causeCanary))
            assertFalse(serializedLogs.contains(fnrCanary))
            val terminalErrors = logs.filter { it["level"]?.asText() == "ERROR" }
            terminalErrors.size shouldBeEqualTo 1
            val terminalError = terminalErrors.single()
            terminalError["event_type"].asText() shouldBeEqualTo
                PDL_PERSONOPPSLAG_FAILED
            terminalError["cause_type"].asText() shouldBeEqualTo "IOException"
            terminalError["kafka_topic"].asText() shouldBeEqualTo sendtSykmeldingTopic
            terminalError["kafka_partition"].asText() shouldBeEqualTo "0"
            terminalError["kafka_offset"].asText() shouldBeEqualTo "0"
            assertFalse(logs.any { it["level"]?.asText() == "WARN" })
            verify(exactly = 1) { kafkaConsumer.seek(TopicPartition(sendtSykmeldingTopic, 0), 0) }
            verify(exactly = 1) {
                kafkaConsumer.pause(setOf(TopicPartition(sendtSykmeldingTopic, 0)))
            }
            verify(exactly = 0) {
                kafkaConsumer.commitSync(
                    any<Map<TopicPartition, OffsetAndMetadata>>(),
                )
            }
            verify(exactly = 0) { kafkaConsumer.unsubscribe() }
            verify(exactly = 1) { kafkaConsumer.close(Duration.ofSeconds(3)) }
        }

        test("retrybar PDL-feil søker tilbake til samme offset uten å melde consumeren ut") {
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val topicPartition = TopicPartition(sendtSykmeldingTopic, 2)
            val record = ConsumerRecord(sendtSykmeldingTopic, 2, 17, "sykmelding-id", "{}")
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            every { kafkaConsumer.poll(any<Duration>()) } returns
                ConsumerRecords(mapOf(topicPartition to listOf(record))) andThenThrows
                WakeupException()
            val sykmeldingService = mockk<SykmeldingService>()
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(record) } throws
                PdlPersonoppslagFailedException(
                    message = "retrybar PDL-feil",
                    retryable = true,
                )
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    sykmeldingService = sykmeldingService,
                )

            runBlocking { commonKafkaService.startConsumer() }

            verify(exactly = 1) { kafkaConsumer.seek(topicPartition, 17) }
            verify(exactly = 0) { kafkaConsumer.unsubscribe() }
            verify(exactly = 1) { kafkaConsumer.pause(setOf(topicPartition)) }
            verify(exactly = 0) {
                kafkaConsumer.commitSync(
                    any<Map<TopicPartition, OffsetAndMetadata>>(),
                )
            }
        }

        test("retry-budget åpner circuit uten å blokkere andre partisjoner") {
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val failedPartition = TopicPartition(sendtSykmeldingTopic, 2)
            val failedRecord =
                ConsumerRecord(sendtSykmeldingTopic, 2, 17, "sykmelding-id", "{}")
            val soknadTopic = "flex.sykepengesoknad"
            val soknadPartition = TopicPartition(soknadTopic, 0)
            val soknadRecord = ConsumerRecord(soknadTopic, 0, 4, "soknad-id", "{}")
            val emptyRecords = ConsumerRecords.empty<String, String>()
            var currentTimeNanos = 0L
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            every { kafkaConsumer.assignment() } returns setOf(failedPartition, soknadPartition)
            every { kafkaConsumer.poll(any<Duration>()) } returns
                ConsumerRecords(mapOf(failedPartition to listOf(failedRecord))) andThen
                ConsumerRecords(mapOf(soknadPartition to listOf(soknadRecord))) andThenAnswer {
                    currentTimeNanos += Duration.ofSeconds(10).toNanos()
                    emptyRecords
                } andThen
                ConsumerRecords(mapOf(failedPartition to listOf(failedRecord))) andThenAnswer {
                    currentTimeNanos += Duration.ofSeconds(10).toNanos()
                    emptyRecords
                } andThen
                ConsumerRecords(mapOf(failedPartition to listOf(failedRecord))) andThenThrows
                WakeupException()
            val sykmeldingService = mockk<SykmeldingService>()
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(failedRecord) } throws
                PdlPersonoppslagFailedException(
                    message = "retrybar PDL-feil",
                    retryable = true,
                )
            val soknadService = mockk<SoknadService>(relaxed = true)
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    sykmeldingService = sykmeldingService,
                    soknadService = soknadService,
                    nanoTime = { currentTimeNanos },
                )

            val logs = captureKafkaFlowLogs { commonKafkaService.startConsumer() }

            coVerify(exactly = 3) {
                sykmeldingService.handleSendtSykmeldingKafkaMessage(failedRecord)
            }
            coVerify(exactly = 1) { soknadService.handleSykepengesoknad(soknadRecord) }
            verify(exactly = 3) { kafkaConsumer.seek(failedPartition, 17) }
            verify(exactly = 3) { kafkaConsumer.pause(setOf(failedPartition)) }
            verify(exactly = 2) { kafkaConsumer.resume(setOf(failedPartition)) }
            verify(exactly = 1) {
                kafkaConsumer.commitSync(
                    mapOf(soknadPartition to OffsetAndMetadata(soknadRecord.offset() + 1)),
                )
            }
            verify(exactly = 0) {
                kafkaConsumer.commitSync(
                    mapOf(failedPartition to OffsetAndMetadata(failedRecord.offset() + 1)),
                )
            }
            val retryLogs =
                logs.filter { it["event_type"]?.asText() == "kafka_pdl_retry_scheduled" }
            retryLogs.map { it["retry_state"].asText() } shouldBeEqualTo
                listOf("short_backoff", "short_backoff", "circuit_open")
            retryLogs.last()["retry_delay_seconds"].asLong() shouldBeEqualTo 900L
        }

        test("circuit cooldown åpner partisjonen igjen og committer etter recovery") {
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val topicPartition = TopicPartition(sendtSykmeldingTopic, 1)
            val record = ConsumerRecord(sendtSykmeldingTopic, 1, 9, "sykmelding-id", "{}")
            val emptyRecords = ConsumerRecords.empty<String, String>()
            var currentTimeNanos = 0L
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            every { kafkaConsumer.assignment() } returns setOf(topicPartition)
            every { kafkaConsumer.poll(any<Duration>()) } returns
                ConsumerRecords(mapOf(topicPartition to listOf(record))) andThenAnswer {
                    currentTimeNanos += Duration.ofMinutes(15).toNanos()
                    emptyRecords
                } andThen
                ConsumerRecords(mapOf(topicPartition to listOf(record))) andThenThrows
                WakeupException()
            val sykmeldingService = mockk<SykmeldingService>()
            var attempts = 0
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(record) } coAnswers {
                attempts++
                if (attempts == 1) {
                    throw PdlPersonoppslagFailedException(
                        message = "permanentklassifisert PDL-feil",
                        retryable = false,
                    )
                }
            }
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    sykmeldingService = sykmeldingService,
                    nanoTime = { currentTimeNanos },
                )

            runBlocking { commonKafkaService.startConsumer() }

            coVerify(exactly = 2) {
                sykmeldingService.handleSendtSykmeldingKafkaMessage(record)
            }
            verify(exactly = 1) { kafkaConsumer.seek(topicPartition, record.offset()) }
            verify(exactly = 1) { kafkaConsumer.pause(setOf(topicPartition)) }
            verify(exactly = 1) { kafkaConsumer.resume(setOf(topicPartition)) }
            verify(exactly = 1) {
                kafkaConsumer.commitSync(
                    mapOf(topicPartition to OffsetAndMetadata(record.offset() + 1)),
                )
            }
        }

        test("mislykket half-open probe setter bare partisjonen i karantene") {
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val topicPartition = TopicPartition(sendtSykmeldingTopic, 1)
            val record = ConsumerRecord(sendtSykmeldingTopic, 1, 9, "sykmelding-id", "{}")
            val soknadTopic = "flex.sykepengesoknad"
            val soknadPartition = TopicPartition(soknadTopic, 0)
            val soknadRecord = ConsumerRecord(soknadTopic, 0, 4, "soknad-id", "{}")
            val emptyRecords = ConsumerRecords.empty<String, String>()
            var currentTimeNanos = 0L
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            val rebalanceListener = slot<ConsumerRebalanceListener>()
            every {
                kafkaConsumer.subscribe(
                    any<Collection<String>>(),
                    capture(rebalanceListener),
                )
            } returns Unit
            every { kafkaConsumer.assignment() } returns setOf(topicPartition, soknadPartition)
            every { kafkaConsumer.poll(any<Duration>()) } returns
                ConsumerRecords(mapOf(topicPartition to listOf(record))) andThenAnswer {
                    currentTimeNanos += Duration.ofMinutes(15).toNanos()
                    emptyRecords
                } andThen
                ConsumerRecords(mapOf(topicPartition to listOf(record))) andThenAnswer {
                    rebalanceListener.captured.onPartitionsAssigned(
                        setOf(topicPartition, soknadPartition),
                    )
                    ConsumerRecords(mapOf(soknadPartition to listOf(soknadRecord)))
                } andThenAnswer {
                    KAFKA_PDL_QUARANTINED_PARTITIONS.get() shouldBeEqualTo 1.0
                    throw WakeupException()
                }
            val sykmeldingService = mockk<SykmeldingService>()
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(record) } throws
                PdlPersonoppslagFailedException(
                    message = "vedvarende PDL-feil",
                    retryable = true,
                )
            val applicationState = ApplicationState()
            val soknadService = mockk<SoknadService>(relaxed = true)
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    applicationState = applicationState,
                    sykmeldingService = sykmeldingService,
                    soknadService = soknadService,
                    nanoTime = { currentTimeNanos },
                    maxImmediatePdlAttempts = 1,
                )
            val terminalFailuresBefore = KAFKA_PDL_TERMINAL_FAILURE_COUNTER.get()

            val logs = captureKafkaFlowLogs { commonKafkaService.startConsumer() }

            val retryLogs =
                logs.filter { it["event_type"]?.asText() == "kafka_pdl_retry_scheduled" }
            retryLogs.map { it["retry_state"].asText() } shouldBeEqualTo
                listOf("circuit_open")
            retryLogs.map { it["retry_delay_seconds"].asLong() } shouldBeEqualTo
                listOf(900L)
            val terminalLog =
                logs.single { it["event_type"]?.asText() == "kafka_pdl_retry_exhausted" }
            terminalLog["level"].asText() shouldBeEqualTo "ERROR"
            terminalLog["terminal_action"].asText() shouldBeEqualTo "partition_quarantined"
            terminalLog["retry_attempt"].asInt() shouldBeEqualTo 2
            assertTrue(applicationState.ready)
            KAFKA_PDL_TERMINAL_FAILURE_COUNTER.get() shouldBeEqualTo terminalFailuresBefore + 1
            KAFKA_PDL_QUARANTINED_PARTITIONS.get() shouldBeEqualTo 0.0
            verify(exactly = 3) { kafkaConsumer.pause(setOf(topicPartition)) }
            verify(exactly = 1) { kafkaConsumer.resume(setOf(topicPartition)) }
            verify(exactly = 5) { kafkaConsumer.poll(any<Duration>()) }
            coVerify(exactly = 2) {
                sykmeldingService.handleSendtSykmeldingKafkaMessage(record)
            }
            coVerify(exactly = 1) { soknadService.handleSykepengesoknad(soknadRecord) }
            verify(exactly = 1) {
                kafkaConsumer.commitSync(
                    mapOf(soknadPartition to OffsetAndMetadata(soknadRecord.offset() + 1)),
                )
            }
            verify(exactly = 1) {
                kafkaConsumer.subscribe(
                    any<Collection<String>>(),
                    any<ConsumerRebalanceListener>(),
                )
            }
            verify(exactly = 0) { kafkaConsumer.unsubscribe() }
            verify(exactly = 0) {
                kafkaConsumer.commitSync(
                    mapOf(topicPartition to OffsetAndMetadata(record.offset() + 1)),
                )
            }
        }

        test("MockConsumer bevarer feilet offset mens en annen partisjon committes") {
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val failedPartition = TopicPartition(sendtSykmeldingTopic, 0)
            val failedRecord =
                ConsumerRecord(sendtSykmeldingTopic, 0, 17, "sykmelding-id", "{}")
            val soknadTopic = "flex.sykepengesoknad"
            val successfulPartition = TopicPartition(soknadTopic, 0)
            val successfulRecord = ConsumerRecord(soknadTopic, 0, 4, "soknad-id", "{}")
            val kafkaConsumer = InspectableMockConsumer<String, String>()
            kafkaConsumer.setMaxPollRecords(1)
            kafkaConsumer.updateBeginningOffsets(
                mapOf(failedPartition to 17L, successfulPartition to 4L),
            )
            kafkaConsumer.schedulePollTask {
                kafkaConsumer.rebalance(listOf(failedPartition, successfulPartition))
                kafkaConsumer.addRecord(failedRecord)
            }
            kafkaConsumer.schedulePollTask { kafkaConsumer.addRecord(successfulRecord) }
            val sykmeldingService = mockk<SykmeldingService>()
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(failedRecord) } throws
                PdlPersonoppslagFailedException(
                    message = "retrybar PDL-feil",
                    retryable = true,
                )
            val soknadService = mockk<SoknadService>()
            coEvery { soknadService.handleSykepengesoknad(successfulRecord) } coAnswers {
                kafkaConsumer.wakeup()
            }
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    sykmeldingService = sykmeldingService,
                    soknadService = soknadService,
                )

            runBlocking { commonKafkaService.startConsumer() }

            kafkaConsumer.position(failedPartition) shouldBeEqualTo failedRecord.offset()
            val committedOffsets =
                kafkaConsumer.committed(setOf(failedPartition, successfulPartition))
            assertNull(committedOffsets[failedPartition])
            kafkaConsumer.paused() shouldBeEqualTo setOf(failedPartition)
            committedOffsets.getValue(successfulPartition).offset() shouldBeEqualTo
                successfulRecord.offset() + 1
            kafkaConsumer.requestedCloseTimeout shouldBeEqualTo Duration.ofSeconds(3)
            kafkaConsumer.reallyClose()
        }

        test("MockConsumer håndterer rebalance mens en PDL-partisjon er pauset") {
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val revokedPartition = TopicPartition(sendtSykmeldingTopic, 0)
            val failedRecord =
                ConsumerRecord(sendtSykmeldingTopic, 0, 17, "sykmelding-id", "{}")
            val soknadTopic = "flex.sykepengesoknad"
            val assignedPartition = TopicPartition(soknadTopic, 0)
            val successfulRecord = ConsumerRecord(soknadTopic, 0, 4, "soknad-id", "{}")
            val kafkaConsumer = InspectableMockConsumer<String, String>()
            kafkaConsumer.setMaxPollRecords(1)
            kafkaConsumer.updateBeginningOffsets(
                mapOf(revokedPartition to 17L, assignedPartition to 4L),
            )
            kafkaConsumer.schedulePollTask {
                kafkaConsumer.rebalance(listOf(revokedPartition))
                kafkaConsumer.addRecord(failedRecord)
            }
            kafkaConsumer.schedulePollTask {
                kafkaConsumer.rebalance(listOf(assignedPartition))
                kafkaConsumer.addRecord(successfulRecord)
            }
            val sykmeldingService = mockk<SykmeldingService>()
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(failedRecord) } throws
                PdlPersonoppslagFailedException(
                    message = "retrybar PDL-feil",
                    retryable = true,
                )
            val soknadService = mockk<SoknadService>()
            coEvery { soknadService.handleSykepengesoknad(successfulRecord) } coAnswers {
                kafkaConsumer.wakeup()
            }
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    sykmeldingService = sykmeldingService,
                    soknadService = soknadService,
                )

            runBlocking { commonKafkaService.startConsumer() }

            val committedOffsets =
                kafkaConsumer.committed(setOf(revokedPartition, assignedPartition))
            assertNull(committedOffsets[revokedPartition])
            committedOffsets.getValue(assignedPartition).offset() shouldBeEqualTo
                successfulRecord.offset() + 1
            kafkaConsumer.assignment() shouldBeEqualTo setOf(assignedPartition)
            kafkaConsumer.requestedCloseTimeout shouldBeEqualTo Duration.ofSeconds(3)
            kafkaConsumer.reallyClose()
        }

        test("permanent PDL-feil parkerer bare den berørte partisjonen") {
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val topicPartition = TopicPartition(sendtSykmeldingTopic, 1)
            val record = ConsumerRecord(sendtSykmeldingTopic, 1, 9, "sykmelding-id", "{}")
            val soknadTopic = "flex.sykepengesoknad"
            val soknadRecord = ConsumerRecord(soknadTopic, 0, 4, "soknad-id", "{}")
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            every { kafkaConsumer.poll(any<Duration>()) } returns
                ConsumerRecords(mapOf(topicPartition to listOf(record))) andThen
                ConsumerRecords(
                    mapOf(
                        TopicPartition(soknadTopic, 0) to listOf(soknadRecord),
                    ),
                ) andThenThrows
                WakeupException()
            val sykmeldingService = mockk<SykmeldingService>()
            val soknadService = mockk<SoknadService>(relaxed = true)
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(record) } throws
                PdlPersonoppslagFailedException(
                    message = "permanent PDL-feil",
                    retryable = false,
                )
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    sykmeldingService = sykmeldingService,
                    soknadService = soknadService,
                )

            runBlocking { commonKafkaService.startConsumer() }

            verifyOrder {
                kafkaConsumer.seek(topicPartition, 9)
                kafkaConsumer.pause(setOf(topicPartition))
            }
            coVerify(exactly = 1) { soknadService.handleSykepengesoknad(soknadRecord) }
            verify(exactly = 1) {
                kafkaConsumer.commitSync(
                    mapOf(TopicPartition(soknadTopic, 0) to OffsetAndMetadata(5)),
                )
            }
            verify(exactly = 0) { kafkaConsumer.unsubscribe() }
        }

        test("PDL not_found hoppes over og committes uten ekstra retry-logg") {
            val sendtSykmeldingTopic = "teamsykmelding.syfo-sendt-sykmelding"
            val topicPartition = TopicPartition(sendtSykmeldingTopic, 0)
            val record = ConsumerRecord(sendtSykmeldingTopic, 0, 3, "sykmelding-id", "{}")
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            every { kafkaConsumer.poll(any<Duration>()) } returns
                ConsumerRecords(mapOf(topicPartition to listOf(record))) andThenThrows
                WakeupException()
            val sykmeldingService = mockk<SykmeldingService>()
            coEvery { sykmeldingService.handleSendtSykmeldingKafkaMessage(record) } throws
                NameNotFoundInPdlException("allerede logget")
            val commonKafkaService =
                createCommonKafkaService(
                    kafkaConsumer = kafkaConsumer,
                    sykmeldingService = sykmeldingService,
                )

            val logs = captureKafkaFlowLogs { commonKafkaService.startConsumer() }

            assertTrue(logs.none { it["event_type"]?.asText() == "kafka_pdl_retry_scheduled" })
            verify(exactly = 0) { kafkaConsumer.seek(any(), any<Long>()) }
            verify(exactly = 0) { kafkaConsumer.pause(any<Collection<TopicPartition>>()) }
            verify(exactly = 1) {
                kafkaConsumer.commitSync(
                    mapOf(topicPartition to OffsetAndMetadata(record.offset() + 1)),
                )
            }
            verify(exactly = 0) { kafkaConsumer.unsubscribe() }
        }

        test("kafkaWakeup delegere til underliggende consumer") {
            val kafkaConsumer = mockk<KafkaConsumer<String, String>>(relaxed = true)
            val commonKafkaService = createCommonKafkaService(kafkaConsumer)

            commonKafkaService.kafkaWakeup()

            verify(exactly = 1) { kafkaConsumer.wakeup() }
        }
    })

private fun createCommonKafkaService(
    kafkaConsumer: Consumer<String, String>,
    applicationState: ApplicationState = ApplicationState(),
    sykmeldingService: SykmeldingService = mockk(relaxed = true),
    soknadService: SoknadService = mockk(relaxed = true),
    nanoTime: () -> Long = { 0L },
    pdlRetryBackoff: Duration = Duration.ofSeconds(10),
    pdlCircuitBreakerCooldown: Duration = Duration.ofMinutes(15),
    maxImmediatePdlAttempts: Int = 3,
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
        soknadService = soknadService,
        hendelserService = mockk<HendelserService>(relaxed = true),
        nanoTime = nanoTime,
        pdlRetryBackoff = pdlRetryBackoff,
        pdlCircuitBreakerCooldown = pdlCircuitBreakerCooldown,
        maxImmediatePdlAttempts = maxImmediatePdlAttempts,
        pdlRetryJitterRatio = 0.0,
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

private class InspectableMockConsumer<K, V> : MockConsumer<K, V>(OffsetResetStrategy.EARLIEST) {
    var requestedCloseTimeout: Duration? = null
        private set

    override fun close(timeout: Duration) {
        requestedCloseTimeout = timeout
    }

    fun reallyClose() {
        super.close(Duration.ZERO)
    }
}
