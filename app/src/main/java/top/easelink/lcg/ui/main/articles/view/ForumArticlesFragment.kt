package top.easelink.lcg.ui.main.articles.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.greenrobot.eventbus.EventBus
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.ui.main.articles.viewmodel.ForumArticlesViewModel
import top.easelink.lcg.ui.main.model.OpenArticleEvent
import top.easelink.lcg.ui.main.source.model.Article

class ForumArticlesFragment : Fragment(), ControllableFragment {

    private val viewModel: ForumArticlesViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())
        
        composeView.setContent {
            // 获取真实的 ViewModel 数据，并转换为 Compose 状态
            val articles by viewModel.articles.observeAsState(initial = emptyList())
            
            // 将数据传给刚写好的 Compose UI
            ArticlesListCompose(articles = articles) { url ->
                // 点击事件：发送原有的 EventBus 事件，跳转到帖子详情页
                EventBus.getDefault().post(OpenArticleEvent(url))
            }
        }
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 如果刚进入页面还没有数据，触发一次加载
        // 注意：这里传入的 URL 可能会根据上一个页面传过来的参数而变化。
        // 目前先硬编码一个默认的“精品软件”版块 URL 用于测试。
        if (viewModel.articles.value == null) {
            val defaultUrl = "https://www.52pojie.cn/forum-37-1.html" // 精品软件区
            viewModel.initUrlAndFetch(defaultUrl, ArticleFetcher.FetchType.FETCH_INIT)
        }
    }

    companion object {
        fun newInstance(title: String, url: String, showTab: Boolean = false): ForumArticlesFragment {
            val fragment = ForumArticlesFragment()
            val args = Bundle()
            args.putString("title", title)
            args.putString("url", url)
            args.putBoolean("showTab", showTab)
            fragment.arguments = args
            return fragment
        }
    }
}
