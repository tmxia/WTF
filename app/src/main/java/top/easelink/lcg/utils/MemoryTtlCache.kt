package top.easelink.lcg.utils

import java.util.concurrent.ConcurrentHashMap










class MemoryTtlCache<K, V>(
    private val maxEntries: Int = 32,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {
    private class Entry<V>(val value: V, val expireAt: Long)

    private val cache = ConcurrentHashMap<K, Entry<V>>()

    @Synchronized
    fun put(key: K, value: V, ttlMs: Long = this.ttlMs) {
        evictExpired()
        if (cache.size >= maxEntries) {
            cache.keys.firstOrNull()?.let(cache::remove)
        }
        cache[key] = Entry(value, System.currentTimeMillis() + ttlMs)
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() > entry.expireAt) {
            cache.remove(key)
            return null
        }
        return entry.value
    }

    fun contains(key: K): Boolean = get(key) != null

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    private fun evictExpired() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { it.value.expireAt < now }
    }

    companion object {
        const val DEFAULT_TTL_MS = 5 * 60_000L
    }
}
