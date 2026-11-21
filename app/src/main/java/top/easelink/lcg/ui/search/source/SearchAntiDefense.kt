package top.easelink.lcg.ui.search.source

import org.jsoup.nodes.Document
import top.easelink.lcg.ui.search.model.LCGSearchResults
import kotlin.math.min

/**
 * 站内搜索风控防护：节流/冻结、LRU 结果缓存、关键词规范化、退避与随机延迟、风控页面判定。
 * 仅作用于站内搜索链路，不影响帖子、热榜等其它请求。
 */
internal object SearchAntiDefense {

    // ===== 节流 / 冻结 =====
    private const val MIN_INTERVAL_MS = 1200L          // 两次搜索最小间隔（下限）
    private const val FREEZE_DURATION_MS = 60_000L    // 风控超限后的冻结时长
    private var lastRequestAt = 0L
    private var frozenUntil = 0L

    // ===== LRU 结果缓存（同关键词 + 板块 + 排序命中，减少重复请求） =====
    private const val CACHE_MAX = 24
    private const val CACHE_TTL_MS = 10 * 60_000L

    private data class CacheEntry(val results: LCGSearchResults, val expireAt: Long)

    private val cache = object : LinkedHashMap<String, CacheEntry>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CacheEntry>?
        ): Boolean = size > CACHE_MAX
    }

    // ===== 关键词规范化 =====
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

    // ===== 缓存 =====
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
        // 空结果不入库，避免把坏页面/空页固化进缓存
        if (results.searchResultList.isEmpty()) return
        cache[key] = CacheEntry(results, System.currentTimeMillis() + CACHE_TTL_MS)
    }

    // ===== 节流 / 冻结 =====
    fun isFrozen(): Boolean = System.currentTimeMillis() < frozenUntil

    fun freeze() {
        frozenUntil = System.currentTimeMillis() + FREEZE_DURATION_MS
    }

    /** 占用一次搜索槽位；间隔不足返回 false 表示被限流 */
    @Synchronized
    fun acquireSlot(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRequestAt < MIN_INTERVAL_MS) return false
        lastRequestAt = now
        return true
    }

    // ===== 退避 / 随机延迟（调用方运行在 IO 线程） =====
    /** 指数退避：1s → 2s → 4s，封顶 8s，叠加 0~500ms 抖动 */
    fun backoffSleep(retry: Int) {
        val base = 1000L shl min(retry, 3)
        val jitter = (0L..500L).random()
        Thread.sleep(base + jitter)
    }

    /** 翻页随机人型间隔 2.2~4.8s */
    fun nextPageSleep() {
        Thread.sleep((2200L..4800L).random())
    }

    // ===== 风控判定 =====
    fun isBlockedHttpCode(code: Int): Boolean =
        code == 403 || code == 418 || code == 429 || code == 514

    fun isWafPage(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        return html.contains("wzwsquestion") ||
            html.contains("wzws-waf-cgi") ||
            html.contains("waf_zw_verify") ||
            html.contains("captcha")
    }

    fun detectWaf(doc: Document?): Boolean = isWafPage(doc?.html())
}
