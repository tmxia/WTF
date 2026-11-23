package top.easelink.lcg.ui.main.articles.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.greenrobot.eventbus.EventBus
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.ui.main.article.view.PostPreviewDialog
import top.easelink.lcg.ui.main.articles.viewmodel.ArticleFetcher
import top.easelink.lcg.ui.main.articles.viewmodel.ForumArticlesViewModel
import top.easelink.lcg.ui.main.model.OpenArticleEvent

class ForumArticlesFragment : Fragment(), ControllableFragment {
    private val viewModel: ForumArticlesViewModel by viewModels()
    private var isListAtTop = true

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        composeView.setContent {
            val articles by viewModel.articles.collectAsStateWithLifecycle()
            val tabs by viewModel.threadList.collectAsStateWithLifecycle()
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            val selectedSortName by viewModel.selectedSortName.collectAsStateWithLifecycle()
            val selectedTimeName by viewModel.selectedTimeName.collectAsStateWithLifecycle()
            val sortLocked by viewModel.sortLocked.collectAsStateWithLifecycle()

            val title = arguments?.getString("title") ?: "帖子列表"
            ForumArticlesScreen(
                title = title,
                tabs = tabs,
                articles = articles,
                isLoading = isLoading,
                selectedSortName = selectedSortName,
                selectedTimeName = selectedTimeName,
                sortLocked = sortLocked,
                onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                onTabSelected = { url ->
                    val finalUrl = if (url.isEmpty()) arguments?.getString("url") ?: "" else url
                    viewModel.initUrlAndFetch(finalUrl, ArticleFetcher.FetchType.FETCH_INIT)
                },
                onArticleClick = { url -> EventBus.getDefault().post(OpenArticleEvent(url)) },
                onLoadMore = { viewModel.fetchArticles(ArticleFetcher.FetchType.FETCH_MORE) { _ -> } },
                onSortSelected = { orderby, name -> viewModel.selectSort(orderby, name) },
                onTimeSelected = { days, name -> viewModel.selectTime(days, name) },
                onArticleLongClick = { url -> PostPreviewDialog.newInstance(url).show(parentFragmentManager, PostPreviewDialog.TAG) },
                onScrollPositionChanged = { isAtTop -> isListAtTop = isAtTop }
            )
        }

        return composeView
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
