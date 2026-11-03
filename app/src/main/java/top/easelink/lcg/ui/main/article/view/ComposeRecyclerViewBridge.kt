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
                
                adapter.setData(posts, hasMore)
                
                
                if (newPosts.isNotEmpty()) {
                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    )
}
