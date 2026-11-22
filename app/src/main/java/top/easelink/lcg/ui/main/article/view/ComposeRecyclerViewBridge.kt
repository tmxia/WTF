package top.easelink.lcg.ui.main.article.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import timber.log.Timber
import top.easelink.lcg.ui.main.article.viewmodel.ArticleAdapterListener
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
    customSpamKeywords: List<String> = emptyList(),
    searchKeyword: String = "",
    onPostClick: ((Post) -> Unit)? = null,
    onScrollStateChanged: (Boolean) -> Unit = {},
    onReady: (RecyclerView) -> Unit = {}
) {
    val posts by viewModel.posts.collectAsState()
    val hasMore by viewModel.hasMorePages.collectAsState()

    var filteredPosts = if (showOpOnly) {
        val opAuthor = posts.firstOrNull()?.author
        posts.filter { it.author == opAuthor }
    } else {
        posts
    }

    if (filterSpam) {
        filteredPosts = filteredPosts.filter { post ->
            val plainText = post.content?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
            if (plainText.length < 5) return@filter false
            for (keyword in customSpamKeywords) {
                if (plainText.contains(keyword, ignoreCase = true)) {
                    return@filter false
                }
            }
            true
        }
    }

    if (searchKeyword.isNotBlank()) {
        filteredPosts = filteredPosts.filter { post ->

            val plainText = org.jsoup.Jsoup.parse(post.content ?: "").text()
            plainText.contains(searchKeyword, ignoreCase = true)
        }
    }

    val displayPosts = if (isReverse && filteredPosts.size > 1) {
        listOf(filteredPosts[0]) + filteredPosts.drop(1).reversed()
    } else {
        filteredPosts
    }

    val isFiltering = showOpOnly || filterSpam || searchKeyword.isNotBlank()

    LaunchedEffect(isFiltering, searchKeyword, posts.size, hasMore) {
        if (isFiltering && hasMore) {
            delay(1500)

            if (isFiltering && hasMore) {
                Timber.d("【全量搜索】自动拉取下一页...")
                viewModel.fetchArticlePost(ArticleAdapterListener.Companion.FETCH_POST_MORE, null)
            } else {
                Timber.d("【全量搜索】状态已变更，终止后续拉取")
            }
        }
    }

    // 显式持有适配器引用：倒序/正序切换只改变派生顺序、不改变 posts 集合，
    // AndroidView 的 update 回调不一定随 isReverse 重组重跑；这里用 LaunchedEffect
    // 监听 isReverse 变化，强制按当前展示顺序刷新适配器，避免开关无效。
    var articleAdapter: ArticleAdapter? = null

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                RecyclerView(context).apply {
                    itemAnimator = null
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutManager = LinearLayoutManager(context)
                    setPadding(0, 0, 0, 0)
                    clipToPadding = false
                    overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                    adapter = ArticleAdapter(viewModel, fragment, onPostClick).also { articleAdapter = it }
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
                    articleAdapter = adapter
                    Timber.d("【UI层】更新数据: 结果=${displayPosts.size} 条, hasMore=$hasMore")
                    adapter.isFiltering = isFiltering
                    adapter.searchKeyword = searchKeyword
                    adapter.setData(displayPosts, hasMore)
                }
            }
        )

        LaunchedEffect(isReverse, posts.size, hasMore, searchKeyword, showOpOnly, filterSpam) {
            articleAdapter?.setData(displayPosts, hasMore, forceRefresh = true)
        }

        if (isFiltering && displayPosts.isEmpty() && hasMore) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .zIndex(98f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFE65100), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "正在全量搜索，请稍候...", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }

        if (isFiltering && displayPosts.isEmpty() && !hasMore) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .zIndex(99f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "未找到相关内容", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = if (searchKeyword.isNotBlank()) "已搜索全文，未找到“$searchKeyword”" else "当前过滤条件下无内容", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}
