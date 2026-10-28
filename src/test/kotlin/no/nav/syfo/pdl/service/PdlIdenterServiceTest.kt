package no.nav.syfo.pdl.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.syfo.application.metrics.IDENTER_FALLBACK_COUNTER
import no.nav.syfo.application.metrics.IDENTER_LOOKUP_COUNTER
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.util.objectMapper
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

private const val CURRENT = "12345678910"
private const val OLD = "12345678912"
private const val OTHER = "12345678913"

class PdlIdenterServiceTest :
    FunSpec({
        test("maps shuffled bolk elements by ident, filters groups and deduplicates") {
            IdenterFixture().use { f ->
                f.response = {
                    bolk(
                        element(OTHER, "not_found"),
                        element(
                            CURRENT,
                            entries =
                                listOf(
                                    entry(CURRENT, false),
                                    entry(OLD, true),
                                    entry(OLD, true),
                                    entry(OTHER, false, "AKTORID"),
                                ),
                        ),
                    )
                }
                val result = f.service.hentIdenter(listOf(CURRENT, OTHER))
                assertEquals(setOf(CURRENT, OLD), result[CURRENT])
                assertEquals(setOf(OTHER), result[OTHER])
                assertEquals(1, f.requests)
            }
        }

        test("not_found is a complete singleton, not fallback") {
            IdenterFixture().use { f ->
                f.response = { bolk(element(CURRENT, "not_found")) }
                val before = IDENTER_LOOKUP_COUNTER.labels("not_found").get()
                assertEquals(
                    mapOf(CURRENT to setOf(CURRENT)),
                    f.service.hentIdenter(listOf(CURRENT)),
                )
                assertEquals(before + 1, IDENTER_LOOKUP_COUNTER.labels("not_found").get())
            }
        }

        listOf(
            "multiple current" to listOf(entry(CURRENT, false), entry(OLD, false)),
            "no current" to listOf(entry(CURRENT, true), entry(OLD, true)),
            "requested ident absent" to listOf(entry(OLD, false)),
            "missing history flag" to
                listOf("""{"ident":"$CURRENT","gruppe":"FOLKEREGISTERIDENT"}"""),
        ).forEach { (name, entries) ->
            test("$name falls back") {
                IdenterFixture().use { f ->
                    f.response = { bolk(element(CURRENT, entries = entries)) }
                    assertEquals(setOf(CURRENT), f.service.hentIdenter(listOf(CURRENT))[CURRENT])
                }
            }
        }

        listOf(
            "missing element" to """{"data":{"hentIdenterBolk":[]}}""",
            "duplicate element" to bolk(element(CURRENT), element(CURRENT)),
            "ok with null identer" to bolk(element(CURRENT, entries = null)),
            "unknown code" to bolk(element(CURRENT, "unauthorized")),
            "GraphQL error" to
                """{"errors":[{"message":"$CURRENT","path":["hentIdenterBolk","$CURRENT"]}]}""",
            "malformed JSON" to """{"ident":"$CURRENT","data":""",
        ).forEach { (name, body) ->
            test("$name falls back") {
                IdenterFixture().use { f ->
                    f.response = { body }
                    assertEquals(setOf(CURRENT), f.service.hentIdenter(listOf(CURRENT))[CURRENT])
                }
            }
        }

        test("bad_request falls back and records a bounded reason") {
            IdenterFixture().use { f ->
                f.response = { bolk(element(CURRENT, "bad_request")) }
                val before = IDENTER_FALLBACK_COUNTER.labels("pdl_bad_request").get()
                assertEquals(setOf(CURRENT), f.service.hentIdenter(listOf(CURRENT))[CURRENT])
                assertEquals(before + 1, IDENTER_FALLBACK_COUNTER.labels("pdl_bad_request").get())
            }
        }

        test("GraphQL errors do not discard usable per-ident data") {
            IdenterFixture().use { f ->
                f.response =
                    {
                        """{"data":{"hentIdenterBolk":[${element(
                            CURRENT,
                        )}]},"errors":[{"message":"$OLD"}]}"""
                    }
                assertEquals(setOf(CURRENT, OLD), f.service.hentIdenter(listOf(CURRENT))[CURRENT])
            }
        }

        test("HTTP 500 falls back with no retry despite global retry policy") {
            IdenterFixture().use { f ->
                f.status = HttpStatusCode.InternalServerError
                val before = IDENTER_FALLBACK_COUNTER.labels("pdl_http_error").get()
                assertEquals(setOf(CURRENT), f.service.hentIdenter(listOf(CURRENT))[CURRENT])
                assertEquals(1, f.requests)
                assertEquals(before + 1, IDENTER_FALLBACK_COUNTER.labels("pdl_http_error").get())
            }
        }

        test("per-request timeout falls back without retry") {
            IdenterFixture().use { f ->
                f.responseDelay = 3_000
                val before = IDENTER_FALLBACK_COUNTER.labels("pdl_timeout").get()
                assertEquals(setOf(CURRENT), f.service.hentIdenter(listOf(CURRENT))[CURRENT])
                assertEquals(1, f.requests)
                assertEquals(before + 1, IDENTER_FALLBACK_COUNTER.labels("pdl_timeout").get())
            }
        }

        test("IO failure falls back without retry") {
            IdenterFixture().use { f ->
                f.failure = IOException(CURRENT)
                assertEquals(setOf(CURRENT), f.service.hentIdenter(listOf(CURRENT))[CURRENT])
                assertEquals(1, f.requests)
            }
        }

        test("access token failure falls back for every requested ident") {
            IdenterFixture().use { f ->
                coEvery { f.tokens.getAccessToken(any()) } throws IOException(CURRENT)
                assertEquals(
                    mapOf(CURRENT to setOf(CURRENT), OLD to setOf(OLD)),
                    f.service.hentIdenter(listOf(CURRENT, OLD)),
                )
                assertEquals(0, f.requests)
            }
        }

        test("slow access token falls back within two seconds without calling PDL") {
            IdenterFixture().use { f ->
                coEvery { f.tokens.getAccessToken(any()) } coAnswers {
                    delay(10_000)
                    "token"
                }
                val before = IDENTER_FALLBACK_COUNTER.labels("access_token_failed").get()
                val started = TimeSource.Monotonic.markNow()
                val result = withTimeout(3_500) { f.service.hentIdenter(listOf(CURRENT, OLD)) }
                assertTrue(started.elapsedNow().inWholeMilliseconds in 1_800..3_499)
                assertEquals(mapOf(CURRENT to setOf(CURRENT), OLD to setOf(OLD)), result)
                assertEquals(
                    before + 2,
                    IDENTER_FALLBACK_COUNTER.labels("access_token_failed").get(),
                )
                assertEquals(0, f.requests)
            }
        }

        test("every call looks up all requested identer again") {
            IdenterFixture().use { f ->
                assertEquals(setOf(CURRENT, OLD), f.service.hentIdenter(listOf(CURRENT))[CURRENT])
                f.response = { bolk(element(CURRENT, "not_found")) }
                assertEquals(
                    setOf(CURRENT),
                    f.service.hentIdenter(listOf(CURRENT))[CURRENT],
                )
                assertEquals(listOf(listOf(CURRENT), listOf(CURRENT)), f.batches)
                coVerify(exactly = 2) { f.tokens.getAccessToken("scope") }
            }
        }

        test("chunks at 100, keeps unaffected chunks and deduplicates requested identer") {
            IdenterFixture().use { f ->
                val requested = (0..200).map { (10000000000L + it).toString() }
                f.response = { batch ->
                    if (f.requests == 2) {
                        """{"errors":[{"message":"unavailable"}]}"""
                    } else {
                        bolk(*batch.map { element(it, "not_found") }.toTypedArray())
                    }
                }
                val foundBefore = IDENTER_LOOKUP_COUNTER.labels("not_found").get()
                val fallbackBefore = IDENTER_LOOKUP_COUNTER.labels("fallback").get()
                val result = f.service.hentIdenter(requested + requested.first())
                assertEquals(requested.toSet(), result.keys)
                assertEquals(listOf(100, 100, 1), f.batches.map { it.size })
                assertEquals(foundBefore + 101, IDENTER_LOOKUP_COUNTER.labels("not_found").get())
                assertEquals(fallbackBefore + 100, IDENTER_LOOKUP_COUNTER.labels("fallback").get())
                assertTrue(result.all { (ident, values) -> values == setOf(ident) })
            }
        }

        test(
            "overall deadline includes token time and preserves completed chunks",
        ) {
            IdenterFixture().use { f ->
                val requested = (0..200).map { (10000000000L + it).toString() }
                coEvery { f.tokens.getAccessToken(any()) } coAnswers {
                    delay(1_500)
                    "token"
                }
                f.responseDelay = 1_600
                f.response = { batch ->
                    if (f.requests == 2) delay(10_000)
                    bolk(
                        *batch
                            .map {
                                element(
                                    it,
                                    entries =
                                        listOf(
                                            entry(it, false),
                                            entry((it.toLong() + 1_000).toString(), true),
                                        ),
                                )
                            }.toTypedArray(),
                    )
                }
                val before = IDENTER_FALLBACK_COUNTER.labels("pdl_timeout").get()
                val started = TimeSource.Monotonic.markNow()
                val result = withTimeout(6_500) { f.service.hentIdenter(requested) }
                assertTrue(started.elapsedNow().inWholeMilliseconds in 4_800..5_999)
                val completed =
                    requested.take(100).associateWith {
                        setOf(it, (it.toLong() + 1_000).toString())
                    }
                val fallback = requested.drop(100).associateWith { setOf(it) }
                assertEquals(completed + fallback, result)
                assertEquals(before + 101, IDENTER_FALLBACK_COUNTER.labels("pdl_timeout").get())
                assertEquals(2, f.requests)
                assertEquals(listOf(100, 100), f.batches.map { it.size })
            }
        }

        test("cancellation from PDL or token provider propagates") {
            IdenterFixture().use { f ->
                f.failure = CancellationException("Cancelled")
                assertFailsWith<CancellationException> { f.service.hentIdenter(listOf(CURRENT)) }
                coEvery { f.tokens.getAccessToken(any()) } throws CancellationException("Cancelled")
                assertFailsWith<CancellationException> { f.service.hentIdenter(listOf(CURRENT)) }
            }
        }

        listOf("token", "PDL").forEach { boundary ->
            test("caller deadline during $boundary propagates rather than becoming fallback") {
                IdenterFixture().use { f ->
                    if (boundary == "token") {
                        coEvery { f.tokens.getAccessToken(any()) } coAnswers {
                            delay(10_000)
                            "token"
                        }
                    } else {
                        f.responseDelay = 10_000
                    }
                    val before = IDENTER_LOOKUP_COUNTER.labels("fallback").get()
                    assertFailsWith<TimeoutCancellationException> {
                        withTimeout(250) { f.service.hentIdenter(listOf(CURRENT)) }
                    }
                    assertEquals(before, IDENTER_LOOKUP_COUNTER.labels("fallback").get())
                    assertEquals(if (boundary == "token") 0 else 1, f.requests)
                }
            }
        }

        test("empty input does not call dependencies") {
            IdenterFixture().use { f ->
                assertTrue(f.service.hentIdenter(emptyList()).isEmpty())
                assertEquals(0, f.requests)
                coVerify(exactly = 0) { f.tokens.getAccessToken(any()) }
            }
        }

        test("multiple fallbacks log one WARN with counts per reason and no identer") {
            val log = LoggerFactory.getLogger(PdlIdenterService::class.java) as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            log.addAppender(appender)
            try {
                IdenterFixture().use { f ->
                    f.response = {
                        bolk(
                            element(CURRENT, "bad_request"),
                            element(OLD, "bad_request"),
                            element(OTHER, entries = null),
                        )
                    }
                    val badRequestBefore = IDENTER_FALLBACK_COUNTER.labels("pdl_bad_request").get()
                    val incompleteBefore = IDENTER_FALLBACK_COUNTER.labels("pdl_incomplete").get()
                    val lookupBefore = IDENTER_LOOKUP_COUNTER.labels("fallback").get()
                    assertEquals(
                        listOf(CURRENT, OLD, OTHER).associateWith { setOf(it) },
                        f.service.hentIdenter(listOf(CURRENT, OLD, OTHER)),
                    )
                    val warning = appender.list.filter { it.level == Level.WARN }.single()
                    assertTrue(warning.formattedMessage.contains("3 identer"))
                    assertTrue(warning.formattedMessage.contains("pdl_bad_request=2"))
                    assertTrue(warning.formattedMessage.contains("pdl_incomplete=1"))
                    assertFalse(
                        Regex(
                            "\\d{11}",
                        ).containsMatchIn(appender.list.joinToString { it.formattedMessage }),
                    )
                    assertEquals(
                        badRequestBefore + 2,
                        IDENTER_FALLBACK_COUNTER.labels("pdl_bad_request").get(),
                    )
                    assertEquals(
                        incompleteBefore + 1,
                        IDENTER_FALLBACK_COUNTER.labels("pdl_incomplete").get(),
                    )
                    assertEquals(lookupBefore + 3, IDENTER_LOOKUP_COUNTER.labels("fallback").get())
                }
            } finally {
                log.detachAppender(appender)
                appender.stop()
            }
        }

        test("lookup logs contain no 11-digit ident on success and all failure boundaries") {
            val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
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
                    this.encoder = encoder
                    setOutputStream(outputStream)
                    start()
                }
            root.addAppender(appender)
            try {
                IdenterFixture().use { f ->
                    f.service.hentIdenter(listOf(CURRENT))
                    f.response = { bolk(element(CURRENT, "bad_request")) }
                    f.service.hentIdenter(listOf(CURRENT))
                    f.response = { bolk(element(CURRENT, entries = null)) }
                    f.service.hentIdenter(listOf(CURRENT))
                    f.response = { """{"ident":"$CURRENT","data":""" }
                    f.service.hentIdenter(listOf(CURRENT))
                    f.response = { """{"errors":[{"message":"$CURRENT","ident":"$OLD"}]}""" }
                    f.service.hentIdenter(listOf(CURRENT))
                    f.status = HttpStatusCode.InternalServerError
                    f.service.hentIdenter(listOf(CURRENT))
                    f.status = HttpStatusCode.OK
                    f.responseDelay = 3_000
                    f.service.hentIdenter(listOf(CURRENT))
                    f.responseDelay = 0
                    f.failure = IOException(CURRENT)
                    f.service.hentIdenter(listOf(CURRENT))
                    f.failure = null
                    f.response = { bolk(element(CURRENT)) }
                    f.service.hentIdenter(listOf(CURRENT))
                    coEvery { f.tokens.getAccessToken(any()) } throws IOException(CURRENT)
                    f.service.hentIdenter(listOf(CURRENT))
                }
                val output = outputStream.toString(Charsets.UTF_8)
                assertTrue(output.contains("using exact match"))
                assertFalse(Regex("\\d{11}").containsMatchIn(output))
            } finally {
                root.detachAppender(appender)
                appender.stop()
                encoder.stop()
            }
        }
    })

private class IdenterFixture : AutoCloseable {
    val tokens = mockk<AccessTokenClient>()
    var requests = 0
    val batches = mutableListOf<List<String>>()
    var status = HttpStatusCode.OK
    var failure: Exception? = null
    var responseDelay = 0L
    var response: suspend (List<String>) -> String = { bolk(element(CURRENT)) }
    private val engine =
        MockEngine { request ->
            requests++
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            assertEquals("SYM", request.headers["TEMA"])
            assertEquals("B229", request.headers["Behandlingsnummer"])
            val json = objectMapper.readTree(request.body.toByteArray())
            val query = json["query"].asText()
            assertTrue(query.contains("query(\$identer: [ID!]!)"))
            assertTrue(query.contains("grupper: [FOLKEREGISTERIDENT]"))
            assertTrue(query.contains("historikk: true"))
            assertEquals(
                "application/json",
                request.body.contentType
                    ?.withoutParameters()
                    ?.toString(),
            )
            val batch = json["variables"]["identer"].map { it.asText() }
            batches.add(batch)
            failure?.let { throw it }
            delay(responseDelay)
            respond(response(batch), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    private val http =
        HttpClient(engine) {
            install(ContentNegotiation) { jackson { registerKotlinModule() } }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(3)
                delayMillis { 1 }
            }
            install(HttpTimeout) { requestTimeoutMillis = 40_000 }
        }
    val service =
        PdlIdenterService(PdlClient(http, "https://pdl.test/graphql"), tokens, "scope")

    init {
        coEvery { tokens.getAccessToken(any()) } returns "token"
    }

    override fun close() = http.close()
}

private fun bolk(vararg elements: String) =
    """{"data":{"hentIdenterBolk":[${elements.joinToString(",")}]}}"""

private fun element(
    ident: String,
    code: String = "ok",
    entries: List<String>? =
        if (code == "ok") listOf(entry(CURRENT, false), entry(OLD, true)) else null,
): String {
    val serializedEntries = entries?.joinToString(",", "[", "]") ?: "null"
    return """{"ident":"$ident","code":"$code","identer":$serializedEntries}"""
}

private fun entry(
    ident: String,
    historisk: Boolean,
    gruppe: String = "FOLKEREGISTERIDENT",
) = """{"ident":"$ident","historisk":$historisk,"gruppe":"$gruppe"}"""
