package top.easelink.lcg.ui.main.article.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment
import top.easelink.lcg.ui.main.article.viewmodel.ArticleViewModel

@Composable
fun ComposeRecyclerViewBridge(
    modifier: Modifier = Modifier,
    viewModel: ArticleViewModel,
    fragment: Fragment,
    isReverse: Boolean = false
) {
    val posts by viewModel.posts.observeAsState(initial = emptyList())
    val hasMore by viewModel.hasMorePages.observeAsState(initial = false)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                setPadding(0, 0, 0, 0)
                clipToPadding = false
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                adapter = ArticleAdapter(viewModel, fragment)
            }
        },
        update = { recyclerView ->
            val adapter = recyclerView.adapter as? ArticleAdapter
            if (adapter != null) {
                // 核心修复：在数据源头进行倒序反转，彻底告别崩溃和闪烁
                val displayPosts = if (isReverse && posts.size > 1) {
                    // 主楼(post[0])永远在最前，其余的回复进行反转
                    listOf(posts[0]) + posts.drop(1).reversed()
                } else {
                    posts
                }
                // 交给 DiffUtil 进行平滑更新，不再调用 setReverse
                adapter.setData(displayPosts, hasMore)
            }
        }
        }
    )
}
