package no.nav.syfo.pdl.cache

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.valkey.Jedis
import io.valkey.JedisPool
import io.valkey.Pipeline
import io.valkey.Response
import io.valkey.exceptions.JedisConnectionException
import io.valkey.exceptions.JedisDataException
import kotlinx.coroutines.CancellationException
import no.nav.syfo.application.metrics.IDENTER_CACHE_COUNTER
import no.nav.syfo.util.objectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Focused failure/serialization cases complement ValkeyIdenterCacheIntegrationTest.
class ValkeyIdenterCacheTest :
    FunSpec({
        val ident = "12345678910"
        val old = "12345678912"

        test("writes JSON lists using versioned keys and SETEX 300, reads with MGET") {
            val pool = mockk<JedisPool>(relaxed = true)
            val jedis = mockk<Jedis>(relaxed = true)
            val pipeline = mockk<Pipeline>(relaxed = true)
            every { pool.resource } returns jedis
            every { jedis.pipelined() } returns pipeline
            every { jedis.mget("$IDENTER_CACHE_PREFIX$ident", "$IDENTER_CACHE_PREFIX$old") } returns
                listOf("""["$ident","$old"]""", null)
            ValkeyIdenterCache(pool).use { cache ->
                cache.put(mapOf(ident to setOf(ident, old)))
                assertEquals(mapOf(ident to setOf(ident, old)), cache.get(listOf(ident, old)))
            }
            verify(exactly = 1) {
                pipeline.setex(
                    "$IDENTER_CACHE_PREFIX$ident",
                    300L,
                    match {
                        objectMapper.readTree(it).map { node -> node.asText() }.toSet() ==
                            setOf(ident, old)
                    },
                )
                pipeline.sync()
                pipeline.close()
                pool.close()
            }
            verify(exactly = 2) { jedis.close() }
        }

        listOf(
            "[]",
            """["12345678912"]""",
            """{"ident":"12345678910"}""",
            """["12345678910",null]""",
        ).forEachIndexed { index, json ->
            test("invalid cached answer $index is treated as a miss") {
                val pool = mockk<JedisPool>(relaxed = true)
                val jedis = mockk<Jedis>(relaxed = true)
                every { pool.resource } returns jedis
                every { jedis.mget("$IDENTER_CACHE_PREFIX$ident") } returns listOf(json)
                ValkeyIdenterCache(pool).use { cache ->
                    assertTrue(cache.get(listOf(ident)).isEmpty())
                }
            }
        }

        test("any Valkey exception on read or write is contained") {
            val pool = mockk<JedisPool>(relaxed = true)
            every { pool.resource } throws JedisConnectionException("Unavailable")
            ValkeyIdenterCache(pool).use { cache ->
                assertTrue(cache.get(listOf(ident)).isEmpty())
                cache.put(mapOf(ident to setOf(ident)))
            }
        }

        test("pipelined command errors are observed and counted without failing the request") {
            val pool = mockk<JedisPool>(relaxed = true)
            val jedis = mockk<Jedis>(relaxed = true)
            val pipeline = mockk<Pipeline>(relaxed = true)
            val response = mockk<Response<String>>()
            every { pool.resource } returns jedis
            every { jedis.pipelined() } returns pipeline
            every { pipeline.setex(any<String>(), any<Long>(), any<String>()) } returns response
            every { response.get() } throws JedisDataException("Unavailable")
            val before = IDENTER_CACHE_COUNTER.labels("error").get()
            ValkeyIdenterCache(pool).use { cache ->
                cache.put(mapOf(ident to setOf(ident)))
            }
            assertEquals(before + 1, IDENTER_CACHE_COUNTER.labels("error").get())
        }

        test("cancellation is not swallowed") {
            val pool = mockk<JedisPool>(relaxed = true)
            every { pool.resource } throws CancellationException("Cancelled")
            ValkeyIdenterCache(pool).use { cache ->
                assertFailsWith<CancellationException> { cache.get(listOf(ident)) }
                assertFailsWith<CancellationException> { cache.put(mapOf(ident to setOf(ident))) }
            }
        }
    })
