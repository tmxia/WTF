package top.easelink.lcg.ui.search.source

import kotlinx.coroutines.delay
import org.jsoup.nodes.Document
import top.easelink.lcg.ui.search.model.LCGSearchResults
import kotlin.math.min





internal object SearchAntiDefense {

    
    private const val MIN_INTERVAL_MS = 1200L          
    private const val FREEZE_DURATION_MS = 60_000L    
    private var lastRequestAt = 0L
    private var frozenUntil = 0L

    
    private const val CACHE_MAX = 24
    private const val CACHE_TTL_MS = 10 * 60_000L

    private data class CacheEntry(val results: LCGSearchResults, val expireAt: Long)

    private val cache = object : LinkedHashMap<String, CacheEntry>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CacheEntry>?
        ): Boolean = size > CACHE_MAX
    }

    
    fun normalizeKeyword(raw: String?): String {
        if (raw == null) return ""
        return raw
            .replace(Regex("[\\r\\n\\t\\u0000-\\u001f]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(80)
    }

    fun cacheKey(keyword: String, fid: String?, orderby: String?): String =
        "${keyword.trim()}|${fid.orEmpty()}|${orderby.orEmpty()}"

    
    @Synchronized
    fun getCached(key: String): LCGSearchResults? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() > entry.expireAt) {
            cache.remove(key)
            return null
        }
        return entry.results
    }

    @Synchronized
    fun putCached(key: String, results: LCGSearchResults) {
        
        if (results.searchResultList.isEmpty()) return
        cache[key] = CacheEntry(results, System.currentTimeMillis() + CACHE_TTL_MS)
    }

    
    fun isFrozen(): Boolean = System.currentTimeMillis() < frozenUntil

    fun freeze() {
        frozenUntil = System.currentTimeMillis() + FREEZE_DURATION_MS
    }

    
    @Synchronized
    fun acquireSlot(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRequestAt < MIN_INTERVAL_MS) return false
        lastRequestAt = now
        return true
    }

    
    
    suspend fun backoffSleep(retry: Int) {
        val base = 1000L shl min(retry, 3)
        val jitter = (0L..500L).random()
        delay(base + jitter)
    }

    
    suspend fun nextPageSleep() {
        delay((2200L..4800L).random())
    }

    
    fun isBlockedHttpCode(code: Int): Boolean =
        code == 403 || code == 418 || code == 429 || code == 514

    fun isWafPage(html: String?): Boolean {
        // [ARCH-PHASE-1] 统一收口到 WafSolver
        return top.easelink.lcg.utils.WafSolver.isWafPage(html)
    }

    fun detectWaf(doc: Document?): Boolean = isWafPage(doc?.html())
}
