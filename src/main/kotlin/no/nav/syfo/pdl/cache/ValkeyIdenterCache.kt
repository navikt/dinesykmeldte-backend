package no.nav.syfo.pdl.cache

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import io.valkey.DefaultJedisClientConfig
import io.valkey.HostAndPort
import io.valkey.Jedis
import io.valkey.JedisPool
import io.valkey.params.SetParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import no.nav.syfo.pdl.client.model.IdenterResult
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import java.time.Duration
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocketFactory

class ValkeyIdenterCache(
    private val pool: JedisPool,
    private val ttlSeconds: Long = 300,
) : IdenterCache,
    AutoCloseable {
    private val mapper = ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    private val warnings = CacheWarnings()

    init {
        require(ttlSeconds in 1..300)
    }

    override suspend fun read(identer: Set<String>): IdenterCacheRead =
        withContext(Dispatchers.IO) {
            try {
                if (identer.isEmpty()) return@withContext IdenterCacheRead(emptyMap())
                val requested = identer.toList()
                val values = pool.resource.use { it.mget(*requested.map(::key).toTypedArray()) }
                val hits = mutableMapOf<String, IdenterResult.Complete>()
                val invalid = mutableSetOf<String>()
                requested.zip(values).forEach { (ident, value) ->
                    if (value != null) {
                        val complete = decode(ident, value)
                        if (complete != null) hits[ident] = complete else invalid.add(ident)
                    }
                }
                IdenterCacheRead(hits, invalid)
            } finally {
                // Blocking client failures must not mask a caller's cancellation.
                currentCoroutineContext().ensureActive()
            }
        }

    override suspend fun write(results: Map<String, IdenterResult.Complete>) {
        if (results.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                pool.resource.use { jedis ->
                    jedis.pipelined().use { pipeline ->
                        val writes =
                            results.map { (ident, result) ->
                                require(valid(ident, result.outcome, result.identer))
                                pipeline.set(
                                    key(ident),
                                    mapper.writeValueAsString(
                                        CacheEntry(result.outcome, result.identer),
                                    ),
                                    SetParams.setParams().ex(ttlSeconds),
                                )
                            }
                        pipeline.sync()
                        writes.forEach { check(it.get() == "OK") }
                    }
                }
            } finally {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private fun decode(
        requested: String,
        value: String,
    ): IdenterResult.Complete? =
        try {
            val json = mapper.readTree(value)
            require(json.isObject && json.size() == 2)
            val outcome = json["outcome"]
            val identer = json["identer"]
            require(outcome != null && outcome.isTextual)
            require(identer != null && identer.isArray && identer.all { it.isTextual })
            require(outcome.textValue() != "not_found" || identer.size() == 1)
            val entries = identer.map { it.textValue() }.toSet()
            require(valid(requested, outcome.textValue(), entries))
            IdenterResult.Complete(entries, outcome.textValue())
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            warnings.warn(CacheOperation.DECODE, CacheError.INVALID)
            null
        }

    override fun close() {
        try {
            pool.close()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            warnings.warn(CacheOperation.CLOSE, CacheError.UNAVAILABLE)
        }
    }

    companion object {
        fun create(config: ValkeyConfig): ValkeyIdenterCache =
            ValkeyIdenterCache(createPool(config))

        internal fun createPool(
            config: ValkeyConfig,
            sslSocketFactory: SSLSocketFactory? = null,
        ): JedisPool {
            val poolConfig =
                GenericObjectPoolConfig<Jedis>().apply {
                    maxTotal = 8
                    maxIdle = 8
                    minIdle = 0
                    setMaxWait(Duration.ofMillis(100))
                    // No validation roundtrips or background connection creation.
                    testOnBorrow = false
                    testWhileIdle = false
                }
            val clientConfig =
                DefaultJedisClientConfig
                    .builder()
                    .connectionTimeoutMillis(500)
                    .socketTimeoutMillis(500)
                    .user(config.username)
                    .password(config.password)
                    .ssl(config.tls)
                    .sslSocketFactory(sslSocketFactory)
                    .sslParameters(
                        SSLParameters().apply { endpointIdentificationAlgorithm = "HTTPS" },
                    ).build()
            // The Nais HOST points at the primary, never a read replica.
            return JedisPool(poolConfig, HostAndPort(config.host, config.port), clientConfig)
        }

        private fun key(ident: String) = "pdl:identer:v1:$ident"

        private fun valid(
            requested: String,
            outcome: String,
            identer: Set<String>,
        ) = outcome in setOf("found", "not_found") &&
            identer.isNotEmpty() &&
            identer.all { it.isNotBlank() } &&
            requested in identer &&
            (outcome != "not_found" || identer == setOf(requested))
    }
}

private class CacheEntry(
    val outcome: String,
    val identer: Set<String>,
)
