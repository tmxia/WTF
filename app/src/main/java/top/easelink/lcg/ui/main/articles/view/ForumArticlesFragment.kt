package top.easelink.lcg.ui.main.articles.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.greenrobot.eventbus.EventBus
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.ui.main.articles.viewmodel.ArticleFetcher
import top.easelink.lcg.ui.main.articles.viewmodel.ForumArticlesViewModel
import top.easelink.lcg.ui.main.model.OpenArticleEvent
import top.easelink.lcg.ui.main.article.view.PostPreviewDialog
import top.easelink.lcg.ui.main.source.model.ForumThread

class ForumArticlesFragment : Fragment(), ControllableFragment {

    private val viewModel: ForumArticlesViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        
        val swipeRefreshLayout = SwipeRefreshLayout(context).apply {
            
            setDistanceToTriggerSync(200)
            setSlingshotDistance(300)
            
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        composeView.setContent {
            val articles by viewModel.articles.observeAsState(initial = emptyList())
            val tabs by viewModel.threadList.observeAsState(initial = emptyList())
            val isLoading by viewModel.isLoading.observeAsState(initial = false)
            
            
            LaunchedEffect(articles, tabs) {
                swipeRefreshLayout.isRefreshing = false
            }

            val title = arguments?.getString("title") ?: "帖子列表"
            ForumArticlesScreen(
                title = title,
                tabs = tabs,
                articles = articles,
                isLoading = isLoading,
                onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                onTabSelected = { url ->
                    val finalUrl = if (url.isEmpty()) arguments?.getString("url") ?: "" else url
                    viewModel.initUrlAndFetch(finalUrl, ArticleFetcher.FetchType.FETCH_INIT)
                },
                onArticleClick = { url ->
                    EventBus.getDefault().post(OpenArticleEvent(url))
                },
                onLoadMore = { viewModel.fetchArticles(ArticleFetcher.FetchType.FETCH_MORE) { _ -> } },
                onArticleLongClick = { url -> 
                    PostPreviewDialog.newInstance(url).show(parentFragmentManager, PostPreviewDialog.TAG)
                }
            )
        }

        
        swipeRefreshLayout.setOnRefreshListener {
            val url = arguments?.getString("url") ?: ""
            viewModel.initUrlAndFetch(url, ArticleFetcher.FetchType.FETCH_INIT)
        }

        swipeRefreshLayout.addView(composeView)
        return swipeRefreshLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val url = arguments?.getString("url") ?: ""
        // 核心修复：强制首次请求数据，不再被 null 判断卡住
        viewModel.initUrlAndFetch(url, ArticleFetcher.FetchType.FETCH_INIT)
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
