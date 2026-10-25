package no.nav.syfo.pdl.cache

interface IdenterCache {
    suspend fun get(identer: Collection<String>): Map<String, Set<String>>

    suspend fun put(identer: Map<String, Set<String>>)
}

const val IDENTER_CACHE_TTL_SECONDS = 300L
internal const val IDENTER_CACHE_PREFIX = "pdl-identer-v1:"
