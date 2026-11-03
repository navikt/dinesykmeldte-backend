package no.nav.syfo.pdl.cache

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

internal enum class CacheOperation { READ, WRITE, DECODE, CLOSE }

internal enum class CacheError { UNAVAILABLE, INVALID, TIMEOUT }

// One warning per minute per cache instance, without throwable, message, key or payload.
internal class CacheWarnings {
    private val nextWarning = AtomicLong(0)
    private val log = LoggerFactory.getLogger(CacheWarnings::class.java)

    fun warn(
        operation: CacheOperation,
        error: CacheError,
    ) {
        val now = System.nanoTime()
        val next = nextWarning.get()
        if (next != 0L && now - next < 0) return
        if (!nextWarning.compareAndSet(next, now + 60_000_000_000L)) return
        log
            .atWarn()
            .addKeyValue("operation", operation.name.lowercase())
            .addKeyValue("error_class", error.name.lowercase())
            .log("PDL identer cache unavailable or invalid; continuing without affected entries")
    }
}
