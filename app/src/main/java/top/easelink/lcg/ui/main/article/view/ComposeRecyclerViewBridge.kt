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
import top.easelink.lcg.ui.main.source.model.Post

@Composable
fun ComposeRecyclerViewBridge(
    modifier: Modifier = Modifier,
    viewModel: ArticleViewModel,
    fragment: Fragment,
    isReverse: Boolean = false,
    showOpOnly: Boolean = false,
    onScrollStateChanged: (Boolean) -> Unit = {},
    onReady: (RecyclerView) -> Unit = {}
) {
    val posts by viewModel.posts.observeAsState(initial = emptyList<Post>())
    val hasMore by viewModel.hasMorePages.observeAsState(initial = false)

    
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
            }
        },
        update = { recyclerView ->
            val adapter = recyclerView.adapter as? ArticleAdapter
            if (adapter != null) {
                val filteredPosts = if (showOpOnly) {
                    val opAuthor = posts.firstOrNull()?.author
                    posts.filter { it.author == opAuthor }
                } else {
                    posts
                }
                val displayPosts = if (isReverse && filteredPosts.size > 1) {
                    listOf(filteredPosts[0]) + filteredPosts.drop(1).reversed()
                } else {
                    filteredPosts
                }
                
                adapter.setData(displayPosts, hasMore)
            }
        }
    )
}
