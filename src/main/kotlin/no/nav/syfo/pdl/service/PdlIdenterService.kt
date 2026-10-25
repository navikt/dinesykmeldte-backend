package no.nav.syfo.pdl.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import no.nav.syfo.application.metrics.IDENTER_CACHE_COUNTER
import no.nav.syfo.application.metrics.IDENTER_FALLBACK_COUNTER
import no.nav.syfo.application.metrics.IDENTER_LOOKUP_COUNTER
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.pdl.cache.IdenterCache
import no.nav.syfo.pdl.client.IDENTER_LOOKUP_TIMEOUT_MILLIS
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.IdenterFailureReason
import no.nav.syfo.pdl.client.model.IdenterResult
import no.nav.syfo.util.logger
import kotlin.time.TimeSource

private const val ACCESS_TOKEN_TIMEOUT_MILLIS = 2_000L

class PdlIdenterService(
    private val pdlClient: PdlClient,
    private val accessTokenClient: AccessTokenClient,
    private val pdlScope: String,
    private val cache: IdenterCache,
) {
    suspend fun hentIdenter(identer: Collection<String>): Map<String, Set<String>> {
        val requested = identer.toSet()
        if (requested.isEmpty()) return emptyMap()
        val cached =
            try {
                cache.get(requested).filter { (ident, values) ->
                    ident in requested &&
                        ident in values
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                IDENTER_CACHE_COUNTER.labels("error").inc()
                emptyMap()
            }
        val misses = requested - cached.keys
        IDENTER_CACHE_COUNTER.labels("hit").inc(cached.size.toDouble())
        IDENTER_CACHE_COUNTER.labels("miss").inc(misses.size.toDouble())
        // A cache hit is a complete answer; the JSON list does not preserve the upstream code.
        IDENTER_LOOKUP_COUNTER.labels("found").inc(cached.size.toDouble())
        val fresh = lookup(misses)
        val complete =
            fresh
                .mapNotNull { (ident, result) ->
                    (result as? IdenterResult.Complete)?.let { ident to it.identer }
                }.toMap()
        try {
            cache.put(complete)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            IDENTER_CACHE_COUNTER.labels("error").inc()
        }
        val fallbackCounts = mutableMapOf<IdenterFailureReason, Int>()
        val resolved =
            requested.associateWith { ident ->
                cached[ident] ?: when (val result = fresh[ident]) {
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
