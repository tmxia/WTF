package top.easelink.lcg.ui.main.article.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue // 核心修复：补全缺失的 getValue 导包
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment
import top.easelink.lcg.ui.main.article.viewmodel.ArticleViewModel
import top.easelink.lcg.ui.main.source.model.Post // 确保引入了 Post

@Composable
fun ComposeRecyclerViewBridge(
    modifier: Modifier = Modifier,
    viewModel: ArticleViewModel,
    fragment: Fragment,
    isReverse: Boolean = false
) {
    val posts by viewModel.posts.observeAsState(initial = emptyList<Post>()) // 显式指定泛型，修复推导报错
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
            if (!recyclerView.isAttachedToWindow) return@AndroidView
            val adapter = recyclerView.adapter as? ArticleAdapter
            if (adapter != null) {
                // 在数据源进行安全反转
                val displayPosts = if (isReverse && posts.size > 1) {
                    listOf(posts[0]) + posts.drop(1).reversed()
                } else {
                    posts
                }
                // 交给 DiffUtil 进行平滑更新
                adapter.setData(displayPosts, hasMore)
            }
        }
    )
}
