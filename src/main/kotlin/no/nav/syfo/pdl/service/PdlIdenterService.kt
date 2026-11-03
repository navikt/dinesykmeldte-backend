package no.nav.syfo.pdl.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import no.nav.syfo.application.metrics.IDENTER_FALLBACK_COUNTER
import no.nav.syfo.application.metrics.IDENTER_LOOKUP_COUNTER
import no.nav.syfo.application.metrics.PDL_CACHE_COUNTER
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.pdl.cache.CacheError
import no.nav.syfo.pdl.cache.CacheOperation
import no.nav.syfo.pdl.cache.CacheWarnings
import no.nav.syfo.pdl.cache.IdenterCache
import no.nav.syfo.pdl.cache.NoIdenterCache
import no.nav.syfo.pdl.client.IDENTER_LOOKUP_TIMEOUT_MILLIS
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.IdenterFailureReason
import no.nav.syfo.pdl.client.model.IdenterResult
import no.nav.syfo.util.logger
import kotlin.time.TimeSource

private const val ACCESS_TOKEN_TIMEOUT_MILLIS = 2_000L
private const val CACHE_TIMEOUT_MILLIS = 1_200L

class PdlIdenterService(
    private val pdlClient: PdlClient,
    private val accessTokenClient: AccessTokenClient,
    private val pdlScope: String,
    private val cache: IdenterCache = NoIdenterCache,
) {
    private val cacheWarnings = CacheWarnings()

    suspend fun hentIdenter(identer: Collection<String>): Map<String, Set<String>> {
        val requested = identer.toSet()
        if (requested.isEmpty()) return emptyMap()
        val cached =
            try {
                withTimeoutOrNull(CACHE_TIMEOUT_MILLIS) { cache.read(requested) }
                    .also {
                        if (it == null) cacheWarnings.warn(CacheOperation.READ, CacheError.TIMEOUT)
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                cacheWarnings.warn(CacheOperation.READ, CacheError.UNAVAILABLE)
                null
            }
        if (cached == null) {
            PDL_CACHE_COUNTER.labels("identer", "read_error").inc()
        } else {
            requested.forEach { ident ->
                val result =
                    when (ident) {
                        in cached.hits -> "hit"
                        in cached.invalid -> "invalid"
                        else -> "miss"
                    }
                PDL_CACHE_COUNTER.labels("identer", result).inc()
            }
        }
        val hits = cached?.hits.orEmpty()
        val fresh = lookup(requested - hits.keys)
        val complete =
            fresh
                .filterValues { it is IdenterResult.Complete }
                .mapValues { it.value as IdenterResult.Complete }
        if (cached != null && complete.isNotEmpty()) {
            val written =
                try {
                    withTimeoutOrNull(CACHE_TIMEOUT_MILLIS) {
                        cache.write(complete)
                        true
                    } ?: false
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    false
                }
            if (!written) {
                PDL_CACHE_COUNTER.labels("identer", "write_error").inc()
                cacheWarnings.warn(CacheOperation.WRITE, CacheError.UNAVAILABLE)
            }
        }
        val answers = hits + fresh
        val fallbackCounts = mutableMapOf<IdenterFailureReason, Int>()
        val resolved =
            requested.associateWith { ident ->
                when (val result = answers[ident]) {
                    is IdenterResult.Complete -> {
                        IDENTER_LOOKUP_COUNTER.labels(result.outcome).inc()
                        result.identer
                    }

                    else -> {
                        val reason =
                            (result as? IdenterResult.Failure)?.reason
                                ?: IdenterFailureReason.INCOMPLETE
                        IDENTER_LOOKUP_COUNTER.labels("fallback").inc()
                        IDENTER_FALLBACK_COUNTER.labels(reason.metricValue).inc()
                        fallbackCounts[reason] = (fallbackCounts[reason] ?: 0) + 1
                        setOf(ident)
                    }
                }
            }
        if (fallbackCounts.isNotEmpty()) {
            logger().warn(
                "PDL identer lookup incomplete; using exact match for {} identer ({})",
                fallbackCounts.values.sum(),
                fallbackCounts.entries.joinToString { (reason, count) ->
                    "${reason.metricValue}=$count"
                },
            )
        }
        return resolved
    }

    private suspend fun lookup(identer: Set<String>): Map<String, IdenterResult> {
        if (identer.isEmpty()) return emptyMap()
        val started = TimeSource.Monotonic.markNow()
        val token =
            try {
                withTimeoutOrNull(ACCESS_TOKEN_TIMEOUT_MILLIS) {
                    accessTokenClient.getAccessToken(pdlScope)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            } ?: return identer.associateWith {
                IdenterResult.Failure(IdenterFailureReason.ACCESS_TOKEN_FAILED)
            }
        return try {
            pdlClient.hentIdenter(
                identer,
                token,
                timeoutMillis =
                    IDENTER_LOOKUP_TIMEOUT_MILLIS - started.elapsedNow().inWholeMilliseconds,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            identer.associateWith { IdenterResult.Failure(IdenterFailureReason.INCOMPLETE) }
        }
    }
}
