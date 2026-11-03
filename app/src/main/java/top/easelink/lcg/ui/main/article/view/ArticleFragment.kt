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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import top.easelink.lcg.ui.main.article.view.DownloadLinkDialog.Companion.newInstance as newDownloadLinkDialog
import top.easelink.lcg.ui.main.article.view.ReplyPostDialog.Companion.newInstance as newReplyPostDialog
import top.easelink.lcg.ui.main.article.view.ScreenCaptureDialog.Companion.TAG

class ArticleFragment : Fragment(), ControllableFragment {
    var title: String = ""
    var url: String = ""

    private val viewModel: ArticleViewModel by viewModels()

    override fun isControllable(): Boolean = true

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<AppBarLayout>(R.id.app_bar_layout)?.visibility = View.GONE
        (view?.parent as? View)?.fitsSystemWindows = false
    }

    override fun onPause() {
        super.onPause()
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
        composeView.setContentView {
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
    
    
    private fun ComposeView.setContentView(content: @Composable () -> Unit) {
        this.setContent {
            content()
        }
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

    val isLoading by viewModel.isLoading.observeAsState(initial = false)
    val posts by viewModel.posts.observeAsState(initial = emptyList())
    val isFavorited by viewModel.isFavorited.observeAsState(initial = false)
    val blockMessage by viewModel.blockMessage.observeAsState(initial = "")
    val isNotFound by viewModel.isNotFound.observeAsState(initial = false)
    val shouldDisplayPosts by viewModel.shouldDisplayPosts.observeAsState(initial = true)

    var showMenu by remember { mutableStateOf(false) }
    
    val prefs = remember { context.getSharedPreferences("article_settings", android.content.Context.MODE_PRIVATE) }
    var isReverse by remember { mutableStateOf(prefs.getBoolean("is_reverse", false)) }

    DisposableEffect(Unit) {
        val subscriber = object {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onReplyEvent(event: ReplyPostEvent) {
                if (fragment.isAdded) {
                    newReplyPostDialog(event.replyUrl, event.author)
                        .show(fragment.parentFragmentManager, "ReplyPostDialog")
                }
            }
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onCaptureEvent(event: ScreenCaptureEvent) {
                if (fragment.isAdded) {
                    ScreenCaptureDialog.newInstance(event.imagePath)
                        .show(fragment.parentFragmentManager, TAG)
                }
            }
        }
        EventBus.getDefault().register(subscriber)
        onDispose { EventBus.getDefault().unregister(subscriber) }
    }

    Scaffold(
        
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { 
                    Text(
                        text = title, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    ) 
                },
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
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.DarkGray) },
                                text = { Text(if (isReverse) "顺序显示" else "倒序显示", fontSize = 14.sp, color = Color.DarkGray) },
                                onClick = {
                                    showMenu = false
                                    isReverse = !isReverse
                                    prefs.edit().putBoolean("is_reverse", isReverse).apply()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.DarkGray) },
                                text = { Text("刷新", fontSize = 14.sp, color = Color.DarkGray) },
                                onClick = {
                                    showMenu = false
                                    viewModel.fetchArticlePost(ArticleAdapterListener.Companion.FETCH_POST_INIT, null)
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.DarkGray) },
                                text = { Text("在浏览器打开", fontSize = 14.sp, color = Color.DarkGray) },
                                onClick = {
                                    showMenu = false
                                    WebViewActivity.startWebViewWith(WebsiteConstant.SERVER_BASE_URL + articleUrl, context)
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.DarkGray) },
                                text = { Text("提取下载链接", fontSize = 14.sp, color = Color.DarkGray) },
                                onClick = {
                                    showMenu = false
                                    val urls = viewModel.extractDownloadUrl()
                                    if (!urls.isNullOrEmpty() && fragment.isAdded) {
                                        newDownloadLinkDialog(urls).show(fragment.parentFragmentManager, "DownloadLinkDialog")
                                    } else {
                                        android.widget.Toast.makeText(context, R.string.download_link_not_found, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { 
                                    Icon(
                                        if (isFavorited) Icons.Filled.Favorite else Icons.Default.FavoriteBorder, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isFavorited) Color.Red else Color.DarkGray
                                    ) 
                                },
                                text = { Text(if (isFavorited) "取消收藏" else "添加收藏", fontSize = 14.sp, color = Color.DarkGray) },
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
        },
        floatingActionButton = {
            val replyUrl = posts.firstOrNull()?.replyUrl
            if (!isLoading && replyUrl != null && shouldDisplayPosts) {
                FloatingActionButton(
                    onClick = {
                        if (fragment.isAdded) {
                            CommentArticleDialog.newInstance(replyUrl)
                                .show(fragment.parentFragmentManager, "CommentArticleDialog")
                        }
                    },
                    containerColor = Color(0xFF2C2C2C),
                    contentColor = Color.White,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Reply", modifier = Modifier.size(18.dp))
                }
            }
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
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }
            } else if (isNotFound) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "帖子不存在或已被删除", color = Color.Gray)
                }
            } else if (blockMessage.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = blockMessage, color = Color.Red)
                }
            } else if (shouldDisplayPosts) {
                ComposeRecyclerViewBridge(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    fragment = fragment,
                    isReverse = isReverse
                )
            }
        }
    }
}
