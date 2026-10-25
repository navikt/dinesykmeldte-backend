package no.nav.syfo.pdl.cache

import com.fasterxml.jackson.module.kotlin.readValue
import io.valkey.DefaultJedisClientConfig
import io.valkey.HostAndPort
import io.valkey.JedisPool
import io.valkey.JedisPoolConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.ValkeyEnvironment
import no.nav.syfo.application.metrics.IDENTER_CACHE_COUNTER
import no.nav.syfo.util.objectMapper
import java.time.Duration

class ValkeyIdenterCache(
    private val pool: JedisPool,
) : IdenterCache,
    AutoCloseable {
    constructor(environment: ValkeyEnvironment) : this(
        JedisPool(
            JedisPoolConfig().apply {
                maxTotal = 8
                maxIdle = 8
                setMaxWait(Duration.ofMillis(500))
            },
            HostAndPort(environment.host, environment.port),
            DefaultJedisClientConfig
                .builder()
                .ssl(environment.ssl)
                .user(environment.username)
                .password(environment.password)
                .connectionTimeoutMillis(500)
                .socketTimeoutMillis(500)
                .build(),
        ),
    )

    override suspend fun get(identer: Collection<String>): Map<String, Set<String>> =
        withContext(Dispatchers.IO) {
            if (identer.isEmpty()) return@withContext emptyMap()
            try {
                val keys = identer.toList()
                pool.resource.use { jedis ->
                    keys
                        .zip(jedis.mget(*keys.map { "$IDENTER_CACHE_PREFIX$it" }.toTypedArray()))
                        .mapNotNull { (ident, json) ->
                            if (json == null) return@mapNotNull null
                            val values = objectMapper.readValue<List<String>>(json).toSet()
                            // Corrupt or mismatched cache entries must not grant access.
                            if (ident in values &&
                                values.none { it.isBlank() }
                            ) {
                                ident to values
                            } else {
                                null
                            }
                        }.toMap()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                IDENTER_CACHE_COUNTER.labels("error").inc()
                emptyMap()
            }
        }

    override suspend fun put(identer: Map<String, Set<String>>) {
        if (identer.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                pool.resource.use { jedis ->
                    jedis.pipelined().use { pipeline ->
                        val responses =
                            identer.map { (ident, values) ->
                                pipeline.setex(
                                    "$IDENTER_CACHE_PREFIX$ident",
                                    IDENTER_CACHE_TTL_SECONDS,
                                    objectMapper.writeValueAsString(values.toList()),
                                )
                            }
                        pipeline.sync()
                        // sync stores command errors in Response; get is needed to observe them.
                        responses.forEach { it.get() }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                IDENTER_CACHE_COUNTER.labels("error").inc()
            }
        }
    }

    override fun close() = pool.close()
}
