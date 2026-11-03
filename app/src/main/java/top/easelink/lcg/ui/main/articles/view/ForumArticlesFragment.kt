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

        if (showTab) {
            // 立即初始化一个默认的“全部”Tab，防止数据回来前页面看着是空的
            setupTabs(emptyList())
        }

        viewModel.threadList.observe(viewLifecycleOwner) { threadList ->
            if (showTab) {
                setupTabs(threadList)
            } else {
                forumTab.visibility = View.GONE
            }
        }

        if (viewModel.articles.value == null) {
            viewModel.initUrlAndFetch(url, ArticleFetcher.FetchType.FETCH_INIT)
        }
    }

    private var isTabListenerAdded = false

    private fun setupTabs(threadList: List<ForumThread>) {
        forumTab.visibility = View.VISIBLE
        forumTab.removeAllTabs()

        // 1. 强制添加唯一的“全部”
        forumTab.addTab(forumTab.newTab().setText("全部").setTag(""))

        // 2. 遍历数据，过滤掉可能重复的“全部”，并添加其他分类
        for (thread in threadList) {
            if (thread.threadName == "全部") continue // 防止重名
            forumTab.addTab(forumTab.newTab().setText(thread.threadName).setTag(thread.threadUrl))
        }

        // 3. 确保监听器只绑定一次，防止切换 Tab 触发 N 次网络请求
        if (!isTabListenerAdded) {
            isTabListenerAdded = true
            forumTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val tagUrl = tab?.tag as? String ?: ""
                    if (tagUrl.isNotEmpty()) {
                        viewModel.initUrlAndFetch(tagUrl, ArticleFetcher.FetchType.FETCH_INIT)
                    } else {
                        // 点击“全部”回到初始 URL
                        val baseUrl = arguments?.getString("url") ?: ""
                        viewModel.initUrlAndFetch(baseUrl, ArticleFetcher.FetchType.FETCH_INIT)
                    }
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
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
