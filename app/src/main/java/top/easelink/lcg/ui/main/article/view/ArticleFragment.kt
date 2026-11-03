package top.easelink.lcg.ui.main.article.view

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.appbar.AppBarLayout
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.article.viewmodel.ArticleAdapterListener
import top.easelink.lcg.ui.main.article.viewmodel.ArticleViewModel
import top.easelink.lcg.ui.main.model.ReplyPostEvent
import top.easelink.lcg.ui.main.model.ScreenCaptureEvent
import top.easelink.lcg.ui.webview.view.WebViewActivity
import top.easelink.lcg.utils.WebsiteConstant

class ArticleFragment : Fragment(), ControllableFragment {
    var title: String = ""
    var url: String = ""

    private val viewModel: ArticleViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onResume() {
        super.onResume()
        // 隐藏 Activity 全局 AppBar，解决双层 Toolbar
        requireActivity().findViewById<AppBarLayout>(R.id.app_bar_layout)?.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        // 恢复 Activity 全局 AppBar
        requireActivity().findViewById<AppBarLayout>(R.id.app_bar_layout)?.visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = arguments?.getString(ARG_URL) ?: ""
        title = arguments?.getString(ARG_TITLE) ?: ""

        viewModel.setUrl(url)
        viewModel.fetchArticlePost(ArticleAdapterListener.Companion.FETCH_POST_INIT, null)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater, container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())
        composeView.setContent {
            ArticleScreen(
                title = viewModel.articleTitle.observeAsState(title.ifEmpty { "帖子详情" }).value ?: "帖子详情",
                articleUrl = url,
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
    articleUrl: String,
    viewModel: ArticleViewModel,
    fragment: Fragment,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // 1. 收集所有旧版存在的 LiveData 状态
    val isLoading by viewModel.isLoading.observeAsState(initial = false)
    val posts by viewModel.posts.observeAsState(initial = emptyList())
    val isFavorited by viewModel.isFavorited.observeAsState(initial = false)
    val blockMessage by viewModel.blockMessage.observeAsState(initial = "")
    val isNotFound by viewModel.isNotFound.observeAsState(initial = false)
    val shouldDisplayPosts by viewModel.shouldDisplayPosts.observeAsState(initial = true)

    var showMenu by remember { mutableStateOf(false) }
    
    // 用于处理 EventBus 事件的状态
    var showReplyDialog by remember { mutableStateOf(false) }
    var replyEventData by remember { mutableStateOf<ReplyPostEvent?>(null) }
    var showCaptureDialog by remember { mutableStateOf(false) }
    var captureImagePath by remember { mutableStateOf("") }

    // 2. 恢复 EventBus 监听
    DisposableEffect(Unit) {
        val subscriber = object {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onReplyEvent(event: ReplyPostEvent) {
                replyEventData = event
                showReplyDialog = true
            }

            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onCaptureEvent(event: ScreenCaptureEvent) {
                captureImagePath = event.imagePath
                showCaptureDialog = true
            }
        }
        EventBus.getDefault().register(subscriber)
        onDispose {
            EventBus.getDefault().unregister(subscriber)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
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
                            // 恢复：浏览器打开
                            DropdownMenuItem(
                                text = { Text("在浏览器打开") },
                                onClick = {
                                    showMenu = false
                                    WebViewActivity.startWebViewWith(WebsiteConstant.SERVER_BASE_URL + articleUrl, context)
                                }
                            )
                            // 恢复：提取下载链接
                            DropdownMenuItem(
                                text = { Text("提取下载链接") },
                                onClick = {
                                    showMenu = false
                                    val urls = viewModel.extractDownloadUrl()
                                    if (!urls.isNullOrEmpty()) {
                                        // 暂时用 Android Toast 兜底，如果你写了 Compose 弹窗可以替换
                                        android.widget.Toast.makeText(context, "提取到链接: ${urls.first()}", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        android.widget.Toast.makeText(context, R.string.download_link_not_found, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            // 恢复：动态收藏文字
                            DropdownMenuItem(
                                text = { Text(if (isFavorited) "取消收藏" else "添加收藏") },
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
                .background(Color(0xFFF5F5F5))
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            if (isLoading && posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (isNotFound) {
                // 恢复：404 提示
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "帖子不存在或已被删除", color = Color.Gray)
                }
            } else if (blockMessage.isNotEmpty()) {
                // 恢复：黑名单拦截提示
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = blockMessage, color = Color.Red)
                }
            } else if (shouldDisplayPosts) {
                // 主体列表渲染
                ComposeRecyclerViewBridge(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    fragment = fragment
                )

                // 恢复：悬浮的“写评论”按钮
                val replyUrl = posts.firstOrNull()?.replyUrl
                if (replyUrl != null) {
                    FloatingActionButton(
                        onClick = {
                            // TODO: 请替换为你现在使用的 Compose 评论弹窗
                            // 例如: showCommentDialog = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Reply")
                    }
                }
            }
        }
    }

    // 处理 EventBus 弹窗
    if (showReplyDialog && replyEventData != null) {
        // TODO: 这里替换为你现在的 Compose 回复弹窗，并在关闭时设置 showReplyDialog = false
        // 比如: ReplyDialog(replyEventData = replyEventData, onDismiss = { showReplyDialog = false })
    }
    if (showCaptureDialog) {
        // TODO: 这里替换为你现在的 Compose 截图弹窗，并在关闭时设置 showCaptureDialog = false
        // 比如: ScreenCaptureDialog(imagePath = captureImagePath, onDismiss = { showCaptureDialog = false })
    }
}
