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

    // 探针 1：Compose 层收到的原始数据
    Timber.d("【UI层】Compose 重组，收到 ViewModel posts 大小: ${posts.size}, hasMore: $hasMore")

    AndroidView(
        modifier = modifier,
        factory = { context ->
            Timber.d("【UI层】创建 RecyclerView 实例")
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
            if (adapter == null) {
                Timber.e("【UI层】严重错误：adapter 为 null 或类型不匹配！")
                return@AndroidView
            }
            
            Timber.d("【UI层】Bridge update 触发，开始计算过滤数据")
            
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

            // 探针 2：过滤后，准备推给 Adapter 的数据
            Timber.d("【UI层】计算完毕，准备推给 Adapter 的数据量: ${displayPosts.size}")
            adapter.setData(displayPosts, hasMore)
        }
    )
}
