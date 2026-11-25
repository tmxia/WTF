package top.easelink.lcg.ui.main.source

import timber.log.Timber
import top.easelink.lcg.ui.main.articles.source.FavoriteDataSource
import top.easelink.lcg.utils.FavoriteKeyRules

/**
 * 网站收藏的轻量快照（按 tid 去重）。
 * 收藏列表同步后刷新；打开详情页时据此判断"这篇文章在网站上是否已收藏"，
 * 让 App 内的收藏按钮与网站状态一致，而不是只看本地库。
 */
object FavoriteSnapshot {
    private const val TTL_MS = 120_000L

    @Volatile
    private var threadIds: Set<String> = emptySet()

    @Volatile
    private var updatedAt: Long = 0L

    fun isFresh(): Boolean = System.currentTimeMillis() - updatedAt < TTL_MS

    fun contains(threadId: String?): Boolean =
        threadId?.let { threadIds.contains(it) } == true

    fun update(urls: Collection<String>) {
        threadIds = urls.mapNotNull { FavoriteKeyRules.threadId(it) }.toSet()
        updatedAt = System.currentTimeMillis()
    }

    fun markAdded(threadId: String?) {
        threadId ?: return
        threadIds = threadIds + threadId
        updatedAt = System.currentTimeMillis()
    }

    fun markRemoved(threadId: String?) {
        threadId ?: return
        threadIds = threadIds - threadId
        updatedAt = System.currentTimeMillis()
    }

    fun reset() {
        threadIds = emptySet()
        updatedAt = 0L
    }

    /** 拉一次网站收藏刷新快照；失败时保持旧值，不影响本地判断。 */
    suspend fun refresh(): Boolean = runCatching {
        update(FavoriteDataSource.getAllRemoteFavorites().map { it.url })
        true
    }.onFailure { Timber.w(it, "刷新收藏快照失败") }.getOrDefault(false)
}
