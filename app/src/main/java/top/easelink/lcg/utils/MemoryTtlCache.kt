package top.easelink.lcg.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量内存 LRU + TTL 缓存。
 *
 * - 按 key 存取，超容量淘汰最早插入条目；
 * - 过期条目在读写时惰性清理；
 * - 线程安全，可在 IO / 主线程并发访问。
 *
 * 仅作为可复用组件接入业务，不替代既有磁盘文件缓存（热榜 / 预览）。
 */
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
