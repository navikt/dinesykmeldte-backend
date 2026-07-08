package no.nav.syfo.common.kafka

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.hendelser.HendelserService
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.soknad.SoknadService
import no.nav.syfo.sykmelding.SykmeldingService
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

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
): CommonKafkaService {
    val environment = mockk<Environment>()
    every { environment.narmestelederLeesahTopic } returns
        "teamsykmelding.syfo-narmesteleder-leesah"
    every { environment.sendtSykmeldingTopic } returns "teamsykmelding.syfo-sendt-sykmelding"
    every { environment.sykepengesoknadTopic } returns "flex.sykepengesoknad"
    every { environment.hendelserTopic } returns "team-esyfo.dinesykmeldte-hendelser-v2"

    return CommonKafkaService(
        kafkaConsumer = kafkaConsumer,
        applicationState = applicationState,
        environment = environment,
        narmestelederService = mockk<NarmestelederService>(relaxed = true),
        sykmeldingService = mockk<SykmeldingService>(relaxed = true),
        soknadService = mockk<SoknadService>(relaxed = true),
        hendelserService = mockk<HendelserService>(relaxed = true),
    )
}
