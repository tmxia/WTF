package top.easelink.lcg.ui.main.article.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.ui.main.article.viewmodel.ArticleAdapterListener
import top.easelink.lcg.ui.main.article.viewmodel.ArticleViewModel

class ArticleFragment : Fragment(), ControllableFragment {
    var title: String = ""
    var url: String = ""

    private val viewModel: ArticleViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = arguments?.getString(ARG_URL) ?: ""
        title = arguments?.getString(ARG_TITLE) ?: ""

        viewModel.setUrl(url)
        viewModel.fetchArticlePost(ArticleAdapterListener.Companion.FETCH_POST_INIT, null)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())

        composeView.setContent {
            ArticleScreen(
                title = viewModel.articleTitle.observeAsState(title.ifEmpty { "帖子详情" }).value ?: "帖子详情",
                viewModel = viewModel,
                fragment = this@ArticleFragment,
                onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }
            )
        }
        return composeView
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TITLE = "title"

        @JvmStatic
        fun newInstance(url: String, title: String = ""): ArticleFragment {
            return ArticleFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putString(ARG_TITLE, title)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    title: String,
    viewModel: ArticleViewModel,
    fragment: Fragment,
    onBackClick: () -> Unit
) {
    val isLoading by viewModel.isLoading.observeAsState(initial = false)
    val posts by viewModel.posts.observeAsState(initial = emptyList())

    // 菜单展开状态
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        // 核心修复 1：强制忽略系统的 WindowInsets，防止顶部出现巨大空白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        // 核心修复 2：恢复点击事件
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        // 下拉菜单
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                onClick = {
                                    showMenu = false
                                    viewModel.fetchArticlePost(ArticleAdapterListener.Companion.FETCH_POST_INIT, null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("收藏 / 取消收藏") },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleFavorite()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)) // 给整个页面加个底色，防露出黑洞
                .padding(top = paddingValues.calculateTopPadding()) // 只避让顶部 Toolbar，不避让两侧
        ) {
            if (isLoading && posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                ComposeRecyclerViewBridge(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    fragment = fragment
                )
            }
        }
    }
}
