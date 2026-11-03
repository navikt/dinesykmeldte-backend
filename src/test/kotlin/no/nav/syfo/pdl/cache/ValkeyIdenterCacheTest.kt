package no.nav.syfo.pdl.cache

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import io.kotest.core.spec.style.FunSpec
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.valkey.Jedis
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.syfo.Environment
import no.nav.syfo.pdl.client.model.IdenterResult
import no.nav.syfo.pdl.service.IdenterFixture
import no.nav.syfo.pdl.service.MemoryIdenterCache
import no.nav.syfo.plugins.configureDependencies
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koin
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.TimeSource

private const val IDENT = "12345678910"
private const val OLD = "12345678912"
private const val OTHER = "12345678913"
private const val KEY = "pdl:identer:v1:$IDENT"

private class ValkeyContainer :
    GenericContainer<ValkeyContainer>(DockerImageName.parse("valkey/valkey:9.1.3-alpine3.24"))

class ValkeyIdenterCacheTest :
    FunSpec({
        val container =
            ValkeyContainer()
                .withExposedPorts(6379)
                .withCommand("valkey-server", "--save", "", "--appendonly", "no")

        fun config() =
            ValkeyConfig(container.host, container.getMappedPort(6379), null, null, false)

        fun admin() = Jedis(container.host, container.getMappedPort(6379))

        beforeSpec { container.start() }
        afterSpec { container.stop() }
        beforeEach { admin().use { it.flushDB() } }

        test("roundtrip complete outcomes uses fixed TTL, no alias fan-out and no read refresh") {
            ValkeyIdenterCache.create(config()).use { cache ->
                cache.write(
                    mapOf(
                        IDENT to IdenterResult.Complete(setOf(IDENT, OLD), "found"),
                        OTHER to IdenterResult.Complete(setOf(OTHER), "not_found"),
                    ),
                )
                val firstTtl =
                    admin().use {
                        assertEquals(null, it.get("pdl:identer:v1:$OLD"))
                        it.pttl(KEY)
                    }
                assertTrue(firstTtl in 1..300_000)
                delay(150)
                val read = cache.read(setOf(IDENT, OTHER, OLD))
                assertEquals(setOf(IDENT, OLD), read.hits.getValue(IDENT).identer)
                assertEquals("not_found", read.hits.getValue(OTHER).outcome)
                assertFalse(OLD in read.hits)
                assertTrue(read.invalid.isEmpty())
                val remaining = admin().use { it.pttl(KEY) }
                assertTrue(remaining < firstTtl - 100)
            }
        }

        test("expiry is not extended by reads") {
            val shortLived =
                ValkeyIdenterCache(ValkeyIdenterCache.createPool(config()), ttlSeconds = 1)
            shortLived.use { cache ->
                cache.write(mapOf(IDENT to IdenterResult.Complete(setOf(IDENT), "found")))
                assertTrue(admin().use { it.ttl(KEY) } in 0..1)
                delay(600)
                assertEquals(
                    setOf(IDENT),
                    cache
                        .read(setOf(IDENT))
                        .hits
                        .getValue(IDENT)
                        .identer,
                )
                delay(500)
                assertTrue(cache.read(setOf(IDENT)).hits.isEmpty())
                assertEquals(-2L, admin().use { it.ttl(KEY) })
            }
        }

        test("malformed and invalid JSON are per-entry misses, preserving valid siblings") {
            ValkeyIdenterCache.create(config()).use { cache ->
                cache.write(mapOf(OTHER to IdenterResult.Complete(setOf(OTHER), "not_found")))
                val invalid =
                    listOf(
                        """{"outcome":"found","identer":["$IDENT"],"extra":true}""",
                        """{"outcome":"failure","identer":["$IDENT"]}""",
                        """{"outcome":"found","identer":[]}""",
                        """{"outcome":"found","identer":["$OLD"]}""",
                        """{"outcome":"found","identer":["$IDENT",""]}""",
                        """{"outcome":"found","identer":["$IDENT",123]}""",
                        """{"outcome":"found","identer":["$IDENT",null]}""",
                        """{"outcome":"found","identer":"$IDENT"}""",
                        """{"outcome":"not_found","identer":["$IDENT","$OLD"]}""",
                        """{"outcome":"not_found","identer":["$IDENT","$IDENT"]}""",
                        """{"@class":"synthetic.$IDENT","identer":["$IDENT"]}""",
                        """{"outcome":"found","identer":["$IDENT"]} {"extra":true}""",
                        """{"outcome":"$IDENT","identer":""",
                        "null",
                    )
                invalid.forEach { value ->
                    admin().use { it.set(KEY, value) }
                    val read = cache.read(setOf(IDENT, OTHER))
                    assertEquals(setOf(IDENT), read.invalid)
                    assertEquals(setOf(OTHER), read.hits.keys)
                }
            }
        }

        test("exhausted shared pool fails in bounded time and releases resources for later calls") {
            val pool = ValkeyIdenterCache.createPool(config())
            ValkeyIdenterCache(pool).use { cache ->
                val held = (1..8).map { pool.resource }
                try {
                    val started = TimeSource.Monotonic.markNow()
                    assertFailsWith<Exception> { cache.read(setOf(IDENT)) }
                    assertTrue(started.elapsedNow().inWholeMilliseconds in 80..1_000)
                    assertEquals(8, pool.numActive)
                } finally {
                    held.forEach { it.close() }
                }
                assertTrue(cache.read(setOf(IDENT)).hits.isEmpty())
            }
        }

        test(
            "server pause is bounded by socket timeout and service fails open without cache write",
        ) {
            ValkeyIdenterCache.create(config()).use { cache ->
                // Establish the connection before pausing the server.
                cache.read(setOf(IDENT))
                val paused =
                    container.execInContainer("valkey-cli", "CLIENT", "PAUSE", "2000", "ALL")
                assertEquals(0, paused.exitCode)
                IdenterFixture(cache).use { fixture ->
                    val started = TimeSource.Monotonic.markNow()
                    assertEquals(
                        setOf(IDENT, OLD),
                        fixture.service.hentIdenter(listOf(IDENT))[IDENT],
                    )
                    assertTrue(started.elapsedNow().inWholeMilliseconds in 400..1_500)
                    assertEquals(1, fixture.requests)
                }
                assertFailsWith<TimeoutCancellationException> {
                    withTimeout(50) { cache.read(setOf(IDENT)) }
                }
                delay(2_000)
                assertEquals(null, admin().use { it.get(KEY) })
            }
        }

        test("connection refusal fails open in bounded time") {
            val unusedPort = ServerSocket(0).use { it.localPort }
            val unavailable = ValkeyConfig("127.0.0.1", unusedPort, null, null, false)
            ValkeyIdenterCache.create(unavailable).use { cache ->
                IdenterFixture(cache).use { fixture ->
                    val started = TimeSource.Monotonic.markNow()
                    assertEquals(
                        setOf(IDENT, OLD),
                        fixture.service.hentIdenter(listOf(IDENT))[IDENT],
                    )
                    assertTrue(started.elapsedNow().inWholeMilliseconds < 1_500)
                }
            }
        }

        test(
            "production dependency registration shares and closes cache through Ktor Koin lifecycle",
        ) {
            lateinit var cache: IdenterCache
            testApplication {
                application {
                    configureDependencies()
                    val environment = mockk<Environment>()
                    every { environment.valkey } returns config()
                    koin().loadModules(listOf(module { single { environment } }))
                    cache = get()
                    assertSame(get<ValkeyIdenterCache>(), cache)
                }
                startApplication()
                cache.write(mapOf(IDENT to IdenterResult.Complete(setOf(IDENT), "found")))
                assertEquals(
                    setOf(IDENT),
                    cache
                        .read(setOf(IDENT))
                        .hits
                        .getValue(IDENT)
                        .identer,
                )
            }
            assertFailsWith<Exception> { cache.read(setOf(IDENT)) }
        }

        test(
            "encoded logs redact malformed payloads, exception messages and causes, rate limit WARN",
        ) {
            val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
            val context = LoggerFactory.getILoggerFactory() as LoggerContext
            val output = ByteArrayOutputStream()
            val encoder =
                LogstashEncoder().apply {
                    this.context = context
                    start()
                }
            val appender =
                OutputStreamAppender<ILoggingEvent>().apply {
                    this.context = context
                    this.encoder = encoder
                    setOutputStream(output)
                    start()
                }
            root.addAppender(appender)
            try {
                ValkeyIdenterCache.create(config()).use { cache ->
                    admin().use { it.set(KEY, """{"sensitive":"$IDENT","identer":""") }
                    repeat(3) { cache.read(setOf(IDENT)) }
                }
                val failing = MemoryIdenterCache()
                IdenterFixture(failing).use { fixture ->
                    failing.readFailure = IOException("synthetic-secret-$IDENT", IOException(OLD))
                    repeat(3) { fixture.service.hentIdenter(listOf(IDENT)) }
                }
                val failingWrite = MemoryIdenterCache()
                failingWrite.writeFailure = IOException("synthetic-secret-$OLD")
                IdenterFixture(failingWrite).use { it.service.hentIdenter(listOf(IDENT)) }
                val encoded = output.toString(Charsets.UTF_8)
                assertFalse(Regex("\\d{11}").containsMatchIn(encoded))
                assertFalse(encoded.contains("synthetic-secret"))
                assertFalse(encoded.contains("pdl:identer:"))
                assertFalse(encoded.contains("stack_trace"))
                val events = encoded.lines().filter { it.contains("PDL identer cache") }
                assertEquals(3, events.size)
                assertTrue(
                    events.all {
                        it.contains("\"operation\":") &&
                            it.contains("\"error_class\":")
                    },
                )
            } finally {
                root.detachAppender(appender)
                appender.stop()
                encoder.stop()
            }
        }
    })
