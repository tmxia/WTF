package top.easelink.lcg.ui.main.articles.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.tabs.TabLayout
import org.greenrobot.eventbus.EventBus
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.ui.main.articles.viewmodel.ArticleFetcher
import top.easelink.lcg.ui.main.articles.viewmodel.ForumArticlesViewModel
import top.easelink.lcg.ui.main.model.OpenArticleEvent
import top.easelink.lcg.ui.main.source.model.ForumThread
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL

class ForumArticlesFragment : Fragment(), ControllableFragment {

    private val viewModel: ForumArticlesViewModel by viewModels()
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var forumTab: TabLayout

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(top.easelink.lcg.R.layout.fragment_forum_articles, container, false)
        
        toolbar = view.findViewById(top.easelink.lcg.R.id.articleToolbar)
        forumTab = view.findViewById(top.easelink.lcg.R.id.forumTab)
        val composeView = view.findViewById<ComposeView>(top.easelink.lcg.R.id.compose_view)

        // 设置 Toolbar 标题
        val title = arguments?.getString("title") ?: "帖子列表"
        toolbar.title = title
        
        composeView.setContent {
            val articles by viewModel.articles.observeAsState(initial = emptyList())
            ArticlesListCompose(articles = articles) { url ->
                EventBus.getDefault().post(OpenArticleEvent(url))
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val url = arguments?.getString("url") ?: ""
        val showTab = arguments?.getBoolean("showTab") ?: false

        // 监听 ViewModel 里的 threadList (用于渲染顶部 Tab)
        viewModel.threadList.observe(viewLifecycleOwner) { threadList ->
            if (showTab && threadList.isNotEmpty()) {
                setupTabs(threadList)
            } else {
                forumTab.visibility = View.GONE
            }
        }

        if (viewModel.articles.value == null) {
            viewModel.initUrlAndFetch(url, ArticleFetcher.FetchType.FETCH_INIT)
        }
    }

    private fun setupTabs(threadList: List<ForumThread>) {
        if (forumTab.childCount > 0) return // 防止重复添加

        forumTab.visibility = View.VISIBLE
        forumTab.removeAllTabs()

        // 默认加一个“全部”标签
        forumTab.addTab(forumTab.newTab().setText("全部").setTag(""))

        for (thread in threadList) {
            forumTab.addTab(forumTab.newTab().setText(thread.title).setTag(thread.url))
        }

        forumTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val url = tab?.tag as? String ?: ""
                if (url.isNotEmpty()) {
                    // 切换标签重新加载数据
                    viewModel.initUrlAndFetch(url, ArticleFetcher.FetchType.FETCH_INIT)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
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
