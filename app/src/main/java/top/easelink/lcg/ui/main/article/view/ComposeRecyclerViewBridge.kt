package top.easelink.lcg.ui.main.article.view

import androidx.compose.runtime.Composable
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
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                // 彻底取消 RecyclerView 的边距，让内部的 MaterialCardView 自由发挥
                setPadding(0, 0, 0, 0)
                clipToPadding = false
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                // 应用原有的 Adapter
                adapter = ArticleAdapter(viewModel, fragment)
            }
        },
        update = { recyclerView ->
            // 数据更新逻辑
            (recyclerView.adapter as? ArticleAdapter)?.apply {
                // 调用原有的数据更新方法
                // notifyDataSetChanged()
            }
        }
    )
}
