package no.nav.syfo.sykmelding

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import no.nav.syfo.application.metrics.SYKMELDING_TOPIC_ACTION_COUNTER
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.syketilfelle.client.SyfoSyketilfelleClient
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.db.SykmeldingInfo
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
    })
