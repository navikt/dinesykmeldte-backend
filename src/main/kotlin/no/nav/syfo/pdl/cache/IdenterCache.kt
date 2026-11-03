package no.nav.syfo.pdl.cache

import no.nav.syfo.pdl.client.model.IdenterResult

interface IdenterCache {
    suspend fun read(identer: Set<String>): IdenterCacheRead

    suspend fun write(results: Map<String, IdenterResult.Complete>)
}

class IdenterCacheRead(
    val hits: Map<String, IdenterResult.Complete>,
    val invalid: Set<String> = emptySet(),
)

object NoIdenterCache : IdenterCache {
    override suspend fun read(identer: Set<String>) = IdenterCacheRead(emptyMap())

    override suspend fun write(results: Map<String, IdenterResult.Complete>) = Unit
}
