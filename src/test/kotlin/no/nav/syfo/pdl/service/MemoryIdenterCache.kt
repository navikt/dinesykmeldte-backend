package no.nav.syfo.pdl.service

import no.nav.syfo.pdl.cache.IdenterCache
import no.nav.syfo.pdl.cache.IdenterCacheRead
import no.nav.syfo.pdl.client.model.IdenterResult

internal class MemoryIdenterCache : IdenterCache {
    val entries = mutableMapOf<String, Pair<Long, IdenterResult.Complete>>()
    var now = 0L
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    var writes = 0
    var invalid = emptySet<String>()
    var beforeRead: suspend () -> Unit = {}
    var beforeWrite: suspend () -> Unit = {}

    override suspend fun read(identer: Set<String>): IdenterCacheRead {
        beforeRead()
        readFailure?.let { throw it }
        return IdenterCacheRead(
            entries
                .filter { (ident, entry) -> ident in identer && entry.first > now }
                .mapValues { it.value.second },
            invalid.intersect(identer),
        )
    }

    override suspend fun write(results: Map<String, IdenterResult.Complete>) {
        beforeWrite()
        writes++
        writeFailure?.let { throw it }
        results.forEach { (ident, value) -> entries[ident] = (now + 300) to value }
    }
}
