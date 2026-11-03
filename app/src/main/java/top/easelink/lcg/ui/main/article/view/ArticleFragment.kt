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

        // 1. 告诉 ViewModel 当前要加载的 URL
        viewModel.setUrl(url)
        
        // 2. 触发网络请求拉取帖子数据 (FETCH_POST_INIT 是拉取第一页的常量)
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
                onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }
            ) {
                // 这里原来是你写的 ComposeRecyclerViewBridge 等桥接逻辑，
                // 如果你已经写好了，直接替换掉下面这个 Box 即可。
                // 这里放一个占位，确保编译通过。
                ComposeRecyclerViewBridge(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    fragment = this@ArticleFragment
                )
            }
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

// 你的顶层 Compose 函数定义在这里
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = title, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* 更多操作 */ }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            content()
        }
    }
}
