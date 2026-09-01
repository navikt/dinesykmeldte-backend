package no.nav.syfo.sykmelding

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import no.nav.syfo.application.metrics.SYKMELDING_TOPIC_ACTION_COUNTER
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.exceptions.PdlPersonoppslagFailedException
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.syketilfelle.client.SyfoSyketilfelleClient
import no.nav.syfo.syketilfelle.client.SyketilfelleNotFoundException
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldingInfo
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SykmeldingServiceCancellationTest :
    FunSpec({
        test("propagerer cancellation uten feil-logg eller error-metrikk") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)
            val record =
                ConsumerRecord(
                    "sendt-sykmelding",
                    0,
                    0,
                    sykmeldingId,
                    objectMapper.writeValueAsString(sendtSykmelding),
                )
            val sykmeldingDb = mockk<SykmeldingDb>(relaxed = true)
            every { sykmeldingDb.getSykmeldingInfo(sykmeldingId) } returns null
            every { sykmeldingDb.getSykmeldingInfos(sendtSykmelding.kafkaMetadata.fnr) } returns
                listOf(
                    SykmeldingInfo(
                        sykmeldingId = sykmeldingId,
                        latestTom = LocalDate.now().plusDays(10),
                        fnr = sendtSykmelding.kafkaMetadata.fnr,
                    ),
                )
            val pdlPersonService = mockk<PdlPersonService>()
            val cancellation = CancellationException("shutdown")
            coEvery { pdlPersonService.getPerson(any()) } throws cancellation
            val sykmeldingService =
                SykmeldingService(
                    sykmeldingDb = sykmeldingDb,
                    pdlPersonService = pdlPersonService,
                    syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>(),
                    cluster = "prod-gcp",
                )
            val serviceLogger = LoggerFactory.getLogger(SykmeldingService::class.java) as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            serviceLogger.addAppender(appender)
            val errorCounter = SYKMELDING_TOPIC_ACTION_COUNTER.labels("error")
            val errorCountBefore = errorCounter.get()

            val thrown =
                try {
                    assertFailsWith<CancellationException> {
                        sykmeldingService.handleSendtSykmeldingKafkaMessage(record)
                    }
                } finally {
                    serviceLogger.detachAppender(appender)
                    appender.stop()
                }

            assertSame(cancellation, thrown)
            appender.list.count { it.level == Level.ERROR } shouldBeEqualTo 0
            errorCounter.get() shouldBeEqualTo errorCountBefore
        }

        test("propagerer allerede logget PDL not_found uten ekstra ERROR eller metrikk") {
            val sykmeldingId = UUID.randomUUID().toString()
            val sendtSykmelding = getSendtSykmeldingKafkaMessage(sykmeldingId)
            val record =
                ConsumerRecord(
                    "sendt-sykmelding",
                    0,
                    0,
                    sykmeldingId,
                    objectMapper.writeValueAsString(sendtSykmelding),
                )
            val sykmeldingDb = mockk<SykmeldingDb>(relaxed = true)
            every { sykmeldingDb.getSykmeldingInfo(sykmeldingId) } returns null
            every { sykmeldingDb.getSykmeldingInfos(sendtSykmelding.kafkaMetadata.fnr) } returns
                listOf(
                    SykmeldingInfo(
                        sykmeldingId = sykmeldingId,
                        latestTom = LocalDate.now().plusDays(10),
                        fnr = sendtSykmelding.kafkaMetadata.fnr,
                    ),
                )
            val pdlPersonService = mockk<PdlPersonService>()
            val notFound = NameNotFoundInPdlException("allerede logget")
            coEvery { pdlPersonService.getPerson(any()) } throws notFound
            val sykmeldingService =
                SykmeldingService(
                    sykmeldingDb = sykmeldingDb,
                    pdlPersonService = pdlPersonService,
                    syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>(),
                    cluster = "prod-gcp",
                )
            val serviceLogger = LoggerFactory.getLogger(SykmeldingService::class.java) as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            serviceLogger.addAppender(appender)
            val errorCounter = SYKMELDING_TOPIC_ACTION_COUNTER.labels("error")
            val errorCountBefore = errorCounter.get()

            val thrown =
                try {
                    assertFailsWith<NameNotFoundInPdlException> {
                        sykmeldingService.handleSendtSykmeldingKafkaMessage(record)
                    }
                } finally {
                    serviceLogger.detachAppender(appender)
                    appender.stop()
                }

            assertSame(notFound, thrown)
            appender.list.count { it.level == Level.ERROR } shouldBeEqualTo 0
            errorCounter.get() shouldBeEqualTo errorCountBefore
        }

        test("tombstone beholder kilderaden når PDL feiler og fullfører ved replay") {
            val fnr = "12345678910"
            val deletedId = UUID.randomUUID().toString()
            val remainingId = UUID.randomUUID().toString()
            val deletedSykmelding =
                SykmeldingInfo(
                    sykmeldingId = deletedId,
                    latestTom = LocalDate.now().plusDays(20),
                    fnr = fnr,
                )
            val remainingSykmelding =
                SykmeldingInfo(
                    sykmeldingId = remainingId,
                    latestTom = LocalDate.now().plusDays(10),
                    fnr = fnr,
                )
            val sykmeldingDb = mockk<SykmeldingDb>(relaxed = true)
            every { sykmeldingDb.getSykmeldingInfo(deletedId) } returns deletedSykmelding
            every { sykmeldingDb.getSykmeldingInfos(fnr) } returns
                listOf(deletedSykmelding, remainingSykmelding)
            val pdlFailure =
                PdlPersonoppslagFailedException(
                    message = "midlertidig PDL-feil",
                    retryable = true,
                )
            val pdlPersonService = mockk<PdlPersonService>()
            var pdlAttempts = 0
            coEvery { pdlPersonService.getPerson(fnr) } coAnswers {
                pdlAttempts++
                if (pdlAttempts == 1) throw pdlFailure
                PdlPerson(Navn("Syk", null, "Sykesen"))
            }
            val syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>()
            coEvery {
                syfoSyketilfelleClient.finnStartdato(fnr, remainingId)
            } returns LocalDate.now().minusDays(5)
            val sykmeldingService =
                SykmeldingService(
                    sykmeldingDb = sykmeldingDb,
                    pdlPersonService = pdlPersonService,
                    syfoSyketilfelleClient = syfoSyketilfelleClient,
                    cluster = "prod-gcp",
                )

            val thrown =
                assertFailsWith<PdlPersonoppslagFailedException> {
                    sykmeldingService.handleSendtSykmeldingKafkaMessage(deletedId, null)
                }

            assertSame(pdlFailure, thrown)
            verify(exactly = 0) { sykmeldingDb.remove(deletedId) }

            sykmeldingService.handleSendtSykmeldingKafkaMessage(deletedId, null)

            coVerify(exactly = 2) { pdlPersonService.getPerson(fnr) }
            coVerify(exactly = 1) {
                syfoSyketilfelleClient.finnStartdato(fnr, remainingId)
            }
            verify(exactly = 1) {
                sykmeldingDb.insertOrUpdateSykmeldt(
                    match<SykmeldtDbModel> {
                        it.pasientFnr == fnr && it.latestTom == remainingSykmelding.latestTom
                    },
                )
            }
            verify(exactly = 1) { sykmeldingDb.remove(deletedId) }
            coVerifyOrder {
                syfoSyketilfelleClient.finnStartdato(fnr, remainingId)
                sykmeldingDb.insertOrUpdateSykmeldt(any())
                sykmeldingDb.remove(deletedId)
            }
        }

        test("tombstone rydder stale data og fullfører når personen ikke finnes i PDL") {
            val fnr = "12345678910"
            val deletedId = UUID.randomUUID().toString()
            val remainingId = UUID.randomUUID().toString()
            val deletedSykmelding =
                SykmeldingInfo(
                    sykmeldingId = deletedId,
                    latestTom = LocalDate.now().plusDays(20),
                    fnr = fnr,
                )
            val remainingSykmelding =
                SykmeldingInfo(
                    sykmeldingId = remainingId,
                    latestTom = LocalDate.now().plusDays(10),
                    fnr = fnr,
                )
            val sykmeldingDb = mockk<SykmeldingDb>(relaxed = true)
            every { sykmeldingDb.getSykmeldingInfo(deletedId) } returns deletedSykmelding
            every { sykmeldingDb.getSykmeldingInfos(fnr) } returns
                listOf(deletedSykmelding, remainingSykmelding)
            val pdlPersonService = mockk<PdlPersonService>()
            coEvery { pdlPersonService.getPerson(fnr) } throws
                NameNotFoundInPdlException("personen finnes ikke")
            val syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>(relaxed = true)
            val sykmeldingService =
                SykmeldingService(
                    sykmeldingDb = sykmeldingDb,
                    pdlPersonService = pdlPersonService,
                    syfoSyketilfelleClient = syfoSyketilfelleClient,
                    cluster = "prod-gcp",
                )

            sykmeldingService.handleSendtSykmeldingKafkaMessage(deletedId, null)

            verify(exactly = 1) { sykmeldingDb.deleteSykmeldt(fnr) }
            verify(exactly = 1) { sykmeldingDb.remove(deletedId) }
            verify(exactly = 0) { sykmeldingDb.insertOrUpdateSykmeldt(any()) }
            coVerify(exactly = 0) {
                syfoSyketilfelleClient.finnStartdato(any(), any())
            }
            coVerifyOrder {
                pdlPersonService.getPerson(fnr)
                sykmeldingDb.deleteSykmeldt(fnr)
                sykmeldingDb.remove(deletedId)
            }
        }

        test("tombstone fullfører i dev når syketilfelle ikke finnes") {
            val fnr = "12345678910"
            val deletedId = UUID.randomUUID().toString()
            val remainingId = UUID.randomUUID().toString()
            val deletedSykmelding =
                SykmeldingInfo(
                    sykmeldingId = deletedId,
                    latestTom = LocalDate.now().plusDays(20),
                    fnr = fnr,
                )
            val remainingSykmelding =
                SykmeldingInfo(
                    sykmeldingId = remainingId,
                    latestTom = LocalDate.now().plusDays(10),
                    fnr = fnr,
                )
            val sykmeldingDb = mockk<SykmeldingDb>(relaxed = true)
            every { sykmeldingDb.getSykmeldingInfo(deletedId) } returns deletedSykmelding
            every { sykmeldingDb.getSykmeldingInfos(fnr) } returns
                listOf(deletedSykmelding, remainingSykmelding)
            val pdlPersonService = mockk<PdlPersonService>()
            coEvery { pdlPersonService.getPerson(fnr) } returns
                PdlPerson(Navn("Syk", null, "Sykesen"))
            val syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>()
            coEvery {
                syfoSyketilfelleClient.finnStartdato(fnr, remainingId)
            } throws SyketilfelleNotFoundException("ikke funnet")
            val sykmeldingService =
                SykmeldingService(
                    sykmeldingDb = sykmeldingDb,
                    pdlPersonService = pdlPersonService,
                    syfoSyketilfelleClient = syfoSyketilfelleClient,
                    cluster = "dev-gcp",
                )
            val tombstone =
                ConsumerRecord<String, String>(
                    "sendt-sykmelding",
                    0,
                    0,
                    deletedId,
                    null,
                )

            sykmeldingService.handleSendtSykmeldingKafkaMessage(tombstone)

            verify(exactly = 1) { sykmeldingDb.deleteSykmeldt(fnr) }
            verify(exactly = 1) { sykmeldingDb.remove(deletedId) }
            verify(exactly = 0) { sykmeldingDb.insertOrUpdateSykmeldt(any()) }
            coVerifyOrder {
                pdlPersonService.getPerson(fnr)
                syfoSyketilfelleClient.finnStartdato(fnr, remainingId)
                sykmeldingDb.deleteSykmeldt(fnr)
                sykmeldingDb.remove(deletedId)
            }
        }
    })
