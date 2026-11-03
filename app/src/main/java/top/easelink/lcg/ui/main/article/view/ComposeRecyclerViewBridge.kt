package top.easelink.lcg.ui.main.article.view

import androidx.compose.runtime.Composable
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

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                setPadding(0, 0, 0, 0)
                clipToPadding = false
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                // 将 Adapter 挂载到 RecyclerView 上
                adapter = ArticleAdapter(viewModel, fragment)
            }
        },
        update = { recyclerView ->
            // 当 Compose 监听到数据更新时，将数据推给 Adapter
            (recyclerView.adapter as? ArticleAdapter)?.setData(posts, hasMore)
        }
    )
}
