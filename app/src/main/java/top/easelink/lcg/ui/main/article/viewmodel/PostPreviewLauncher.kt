package top.easelink.lcg.ui.main.article.viewmodel

import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.easelink.lcg.ui.main.article.view.PostPreviewDialog
import top.easelink.lcg.ui.main.source.model.PreviewPost
import top.easelink.lcg.ui.main.source.remote.ArticlesRemoteDataSource

/**
 * 帖子简介预览弹窗协调器。
 *
 * 修复长按列表卡片时弹窗空壳先弹出的时序问题：
 * 1. 长按不再立刻 show 弹窗，先在后台预加载简介（命中磁盘缓存则不发网络请求）；
 * 2. 数据就绪后把解析好的 [PreviewPost] 暂存内存，再弹出 [PostPreviewDialog]，
 *    弹窗直接取用暂存数据渲染，不再二次解析 / 二次请求，避免加载过程中露出空窗；
 * 3. 加载失败 / 取消 / Fragment 已销毁直接丢弃，全程不弹窗；
 * 4. 同一帖子并发长按互斥去重，避免重复预加载与弹窗堆叠。
 */
internal object PostPreviewLauncher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loadingUrl: String? = null

    @Volatile
    private var showingUrl: String? = null

    /** 预加载成功、待弹窗取用的简介数据；取用后即移除，避免脏数据与内存滞留。 */
    private val prepared = HashMap<String, PreviewPost>()

    private fun takePrepared(url: String?): PreviewPost? = synchronized(prepared) {
        if (url.isNullOrBlank()) null else prepared.remove(url)
    }

    private fun dropPrepared(url: String?) {
        if (url.isNullOrBlank()) return
        synchronized(prepared) { prepared.remove(url) }
    }

    /** 弹窗 / ViewModel 取用暂存数据；命中返回简介并清除暂存，未命中返回 null 走网络兜底。 */
    fun consumePrepared(url: String?): PreviewPost? = takePrepared(url)

    /** 后台预加载简介，成功后再弹出预览窗；fm 为空、已保存状态或已销毁时不弹出。 */
    fun preview(fm: FragmentManager?, url: String?) {
        if (fm == null || url.isNullOrBlank()) return
        // 同一帖子正在加载或已在展示，直接忽略，避免重复请求与弹窗堆叠
        if (url == loadingUrl || url == showingUrl) return

        loadingUrl = url
        scope.launch {
            val post = try {
                ArticlesRemoteDataSource.getPostPreview(url)
            } catch (e: Exception) {
                Timber.tag("PostPreview").w(e, "简介预加载失败: $url")
                null
            }
            loadingUrl = null

            if (post == null) return@launch
            if (fm.isStateSaved || fm.isDestroyed) {
                dropPrepared(url)
                return@launch
            }

            synchronized(prepared) { prepared[url] = post }
            showingUrl = url
            withContext(Dispatchers.Main) {
                PostPreviewDialog.newInstance(url).show(fm, PostPreviewDialog.TAG)
            }
        }
    }

    /** 弹窗关闭后清除展示态与暂存数据，允许同一帖子再次预览。 */
    fun notifyDismissed(url: String?) {
        if (url != null && showingUrl == url) showingUrl = null
        dropPrepared(url)
    }
}
