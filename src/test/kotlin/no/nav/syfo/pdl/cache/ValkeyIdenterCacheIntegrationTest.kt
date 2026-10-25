package no.nav.syfo.pdl.cache

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.valkey.JedisPool
import no.nav.syfo.ValkeyEnvironment
import no.nav.syfo.application.metrics.IDENTER_CACHE_COUNTER
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.IdenterResult
import no.nav.syfo.pdl.service.PdlIdenterService
import no.nav.syfo.util.objectMapper
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ValkeyContainer :
    GenericContainer<ValkeyContainer>(DockerImageName.parse("valkey/valkey:8.1.3"))

class ValkeyIdenterCacheIntegrationTest :
    FunSpec({
        val ident = "12345678910"
        val old = "12345678912"
        val missing = "12345678913"
        val container = ValkeyContainer().withExposedPorts(6379)
        var cacheResource: ValkeyIdenterCache? = null

        beforeEach {
            container.start()
            cacheResource =
                ValkeyIdenterCache(
                    ValkeyEnvironment(
                        container.host,
                        container.getMappedPort(6379),
                        null,
                        null,
                        false,
                    ),
                )
        }
        afterEach {
            try {
                cacheResource?.close()
            } finally {
                cacheResource = null
                container.stop()
            }
        }

        test("real Valkey stores JSON lists with SETEX 300 and batch reads return only hits") {
            val cache = requireNotNull(cacheResource)
            val complete = mapOf(ident to setOf(ident, old), old to setOf(ident, old))
            assertTrue(cache.get(listOf(ident, old, missing)).isEmpty())
            cache.put(complete)
            assertEquals(complete, cache.get(listOf(ident, old, missing)))

            JedisPool(container.host, container.getMappedPort(6379)).use { pool ->
                pool.resource.use { jedis ->
                    complete.forEach { (key, values) ->
                        val json = objectMapper.readTree(jedis.get("$IDENTER_CACHE_PREFIX$key"))
                        assertTrue(json.isArray)
                        assertEquals(values, json.map { it.asText() }.toSet())
                        assertTrue(jedis.ttl("$IDENTER_CACHE_PREFIX$key") in 1L..300L)
                    }
                    assertEquals(-2L, jedis.ttl("$IDENTER_CACHE_PREFIX$missing"))
                }
            }
        }

        test("stopped Valkey is a miss without throwing and lookup calls PDL directly") {
            val cache = requireNotNull(cacheResource)
            cache.put(mapOf(ident to setOf(ident)))
            assertEquals(mapOf(ident to setOf(ident)), cache.get(listOf(ident)))
            container.stop()

            val before = IDENTER_CACHE_COUNTER.labels("error").get()
            assertTrue(cache.get(listOf(ident)).isEmpty())
            cache.put(mapOf(ident to setOf(ident)))
            assertTrue(IDENTER_CACHE_COUNTER.labels("error").get() > before)

            val pdl = mockk<PdlClient>()
            val tokens = mockk<AccessTokenClient>()
            coEvery { tokens.getAccessToken("scope") } returns "token"
            coEvery { pdl.hentIdenter(setOf(ident), "token", any()) } returns
                mapOf(ident to IdenterResult.Complete(setOf(ident, old), "found"))
            val service = PdlIdenterService(pdl, tokens, "scope", cache)
            assertEquals(mapOf(ident to setOf(ident, old)), service.hentIdenter(listOf(ident)))
            coVerify(exactly = 1) { pdl.hentIdenter(setOf(ident), "token", any()) }
        }
    })
