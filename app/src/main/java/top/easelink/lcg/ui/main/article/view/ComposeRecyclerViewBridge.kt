package top.easelink.lcg.ui.main.article.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment
import timber.log.Timber
import top.easelink.lcg.ui.main.article.viewmodel.ArticleViewModel
import top.easelink.lcg.ui.main.source.model.Post

@Composable
fun ComposeRecyclerViewBridge(
    modifier: Modifier = Modifier,
    viewModel: ArticleViewModel,
    fragment: Fragment,
    isReverse: Boolean = false,
    showOpOnly: Boolean = false,
    filterSpam: Boolean = false,
    searchKeyword: String = "",
    onScrollStateChanged: (Boolean) -> Unit = {},
    onReady: (RecyclerView) -> Unit = {}
) {
    val posts by viewModel.posts.observeAsState(initial = emptyList<Post>())
    val hasMore by viewModel.hasMorePages.observeAsState(initial = false)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutManager = LinearLayoutManager(context)
                setPadding(0, 0, 0, 0)
                clipToPadding = false
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                adapter = ArticleAdapter(viewModel, fragment)
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(rv, dx, dy)
                        onScrollStateChanged(rv.computeVerticalScrollOffset() > 500)
                    }
                })
                onReady(this)
            }
        },
        update = { recyclerView ->
            val adapter = recyclerView.adapter as? ArticleAdapter
            if (adapter == null) return@AndroidView
            
            var filteredPosts = if (showOpOnly) {
                val opAuthor = posts.firstOrNull()?.author
                posts.filter { it.author == opAuthor }
            } else {
                posts
            }

            if (filterSpam) {
                filteredPosts = filteredPosts.filter { post ->
                    val plainText = post.content?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
                    plainText.length >= 5
                }
            }

            if (searchKeyword.isNotBlank()) {
                filteredPosts = filteredPosts.filter { post ->
                    post.content?.contains(searchKeyword, ignoreCase = true) == true
                }
            }

            val displayPosts = if (isReverse && filteredPosts.size > 1) {
                listOf(filteredPosts[0]) + filteredPosts.drop(1).reversed()
            } else {
                filteredPosts
            }

            val isFiltering = showOpOnly || filterSpam || searchKeyword.isNotBlank()
            
            // 核心修复：如果正在过滤，且结果不足 10 条，强行切断 Adapter 的触底加载！
            // 防止 RecyclerView 因为不满一屏而疯狂并发拉取所有页面，导致 WAF 封号
            val safeHasMore = if (isFiltering && displayPosts.size < 10) false else hasMore
            
            Timber.d("【UI层】过滤参数: searchKeyword='$searchKeyword', 结果: ${displayPosts.size} 条, 允许加载更多: $safeHasMore")
            
            adapter.setData(displayPosts, safeHasMore)
        }
    )
}
