package top.easelink.lcg.ui.main.article.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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
    onPostClick: ((Post) -> Unit)? = null,
    onScrollStateChanged: (Boolean) -> Unit = {},
    onReady: (RecyclerView) -> Unit = {}
) {
    val posts by viewModel.posts.observeAsState(initial = emptyList<Post>())
    val hasMore by viewModel.hasMorePages.observeAsState(initial = false)

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

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                RecyclerView(context).apply {
                    // 核心修复：强行关闭 ItemAnimator，彻底干掉数据量剧变导致的卡片消失和卡死！
                    itemAnimator = null
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutManager = LinearLayoutManager(context)
                    setPadding(0, 0, 0, 0)
                    clipToPadding = false
                    overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                    adapter = ArticleAdapter(viewModel, fragment, onPostClick)
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
                if (adapter != null) {
                    val safeHasMore = if (isFiltering) false else hasMore
                    Timber.d("【UI层】更新数据: 结果=${displayPosts.size} 条, safeHasMore=$safeHasMore")
                    adapter.setData(displayPosts, safeHasMore)
                }
            }
        )

        // 干净利落的无结果覆盖层
        if (isFiltering && displayPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .zIndex(99f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "未找到相关内容",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchKeyword.isNotBlank()) "未找到包含“$searchKeyword”的楼层" else "当前过滤条件下无内容",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
