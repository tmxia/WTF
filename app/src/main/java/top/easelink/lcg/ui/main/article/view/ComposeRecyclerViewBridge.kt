package top.easelink.lcg.ui.main.article.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    
    var recyclerViewInstance by remember { mutableStateOf<RecyclerView?>(null) }

    // 顶层过滤逻辑
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

    // 核心修复：使用 LaunchedEffect 强行监听数据变化，直接更新 Adapter，彻底绕过 AndroidView 的生命周期黑盒
    LaunchedEffect(posts, hasMore, searchKeyword, showOpOnly, filterSpam, isReverse) {
        val adapter = recyclerViewInstance?.adapter as? ArticleAdapter
        if (adapter != null) {
            Timber.d("【UI层】强制数据更新，原始 posts 大小: ${posts.size}, 处理后 displayPosts: ${displayPosts.size}")
            adapter.setData(displayPosts, hasMore)
        } else {
            Timber.e("【UI层】强制数据更新失败：Adapter 为空，recyclerViewInstance 是否初始化=${recyclerViewInstance != null}")
        }
    }

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
                recyclerViewInstance = this
                Timber.d("【UI层】RecyclerView 初始化完成，并绑定到状态")
            }
        },
        update = { recyclerView ->
            // 留空，所有的数据更新均交给上方的 LaunchedEffect 处理
            recyclerViewInstance = recyclerView
        }
    )
}
