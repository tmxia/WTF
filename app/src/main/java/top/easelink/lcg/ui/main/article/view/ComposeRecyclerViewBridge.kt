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
    fragment: Fragment
) {
    // 监听 ViewModel 中的数据变化
    val posts by viewModel.posts.observeAsState(initial = emptyList())
    val hasMore by viewModel.hasMorePages.observeAsState(initial = false)

    // 监听新数据的追加（为了平滑动画）
    val newPosts by viewModel.newPostsAppended.observeAsState(initial = emptyList())

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
                // 每次更新时，直接根据最新的 posts 和 hasMore 赋值
                adapter.setData(posts, hasMore)
                
                // 如果加载了更多数据，滚动到底部（可选体验优化）
                if (newPosts.isNotEmpty()) {
                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    )
}
