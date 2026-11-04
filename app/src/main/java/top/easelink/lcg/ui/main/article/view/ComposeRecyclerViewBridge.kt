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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment
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

    // 1. 预处理过滤逻辑
    val isFiltering = showOpOnly || filterSpam || searchKeyword.isNotBlank()
    
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

    // 2. 渲染层包裹
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
                adapter?.setData(displayPosts, hasMore)
            }
        )

        // 3. 终极防灰块遮罩：如果处于搜索/过滤状态，且当前真的没有任何结果，直接盖住底层的灰块骨架屏
        if (isFiltering && displayPosts.isEmpty() && !hasMore) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (searchKeyword.isNotBlank()) "未找到包含该关键字的楼层" else "当前过滤条件下无内容",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请尝试更换关键字或取消过滤",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
