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
import top.easelink.lcg.ui.main.source.repository.ArticlesRepository











internal object PostPreviewLauncher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loadingUrl: String? = null

    @Volatile
    private var showingUrl: String? = null

    
    private val prepared = HashMap<String, PreviewPost>()

    private fun takePrepared(url: String?): PreviewPost? = synchronized(prepared) {
        if (url.isNullOrBlank()) null else prepared.remove(url)
    }

    private fun dropPrepared(url: String?) {
        if (url.isNullOrBlank()) return
        synchronized(prepared) { prepared.remove(url) }
    }

    
    fun consumePrepared(url: String?): PreviewPost? = takePrepared(url)

    
    fun preview(fm: FragmentManager?, url: String?) {
        if (fm == null || url.isNullOrBlank()) return
        
        if (url == loadingUrl || url == showingUrl) return

        loadingUrl = url
        scope.launch {
            val post = try {
                ArticlesRepository.getPostPreview(url)
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

    
    fun notifyDismissed(url: String?) {
        if (url != null && showingUrl == url) showingUrl = null
        dropPrepared(url)
    }
}
