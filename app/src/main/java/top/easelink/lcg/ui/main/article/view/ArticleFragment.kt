package top.easelink.lcg.ui.main.article.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import top.easelink.lcg.utils.SharedPreferencesHelper
import androidx.core.content.edit


import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import timber.log.Timber
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.runtime.LaunchedEffect
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
import top.easelink.lcg.ui.main.source.model.Post
import top.easelink.lcg.ui.main.article.viewmodel.ArticleViewModel
import top.easelink.lcg.ui.main.model.ReplyPostEvent
import top.easelink.lcg.ui.main.model.LikeSuccessEvent
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
        inflater: android.view.LayoutInflater, 
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        
        
        val composeView = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        composeView.setContent {
            val isLoading by viewModel.isLoading.observeAsState(initial = false)
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
    var pendingScrollPost by remember { mutableStateOf<Post?>(null) }
    var showBackToTop by remember { mutableStateOf(false) }
    var showOpOnly by remember { mutableStateOf(false) }
    var filterSpam by remember { mutableStateOf(false) }
    val sp = remember { SharedPreferencesHelper.getUserSp() }
    var customSpamKeywords by remember { mutableStateOf(sp.getStringSet("custom_spam_keywords", emptySet())?.toList() ?: emptyList()) }
    var searchKeyword by remember { mutableStateOf("") }

    // 核心修复：只要进入新的帖子（URL 变化），立刻强制清空所有搜索和过滤状态，防止状态污染
    LaunchedEffect(articleUrl) {
        searchKeyword = ""
        showOpOnly = false
        filterSpam = false
        Timber.d("【详情页】切换到新帖子，已重置所有过滤条件")
    }

    var recyclerView by remember { mutableStateOf<androidx.recyclerview.widget.RecyclerView?>(null) }
    
    val prefs = remember { context.getSharedPreferences("article_settings", android.content.Context.MODE_PRIVATE) }

    // 核心跳转逻辑：当清空搜索且列表恢复全量数据后，滚动到目标楼层
    LaunchedEffect(posts, pendingScrollPost, searchKeyword) {
        val target = pendingScrollPost ?: return@LaunchedEffect
        // 只有当搜索条件和过滤条件全部清空后，才执行滚动
        if (searchKeyword.isBlank() && !filterSpam && !showOpOnly) {
            
            // 延迟 150ms，确保 Compose 已经把全量数据 setData 给了 Adapter
            kotlinx.coroutines.delay(150)
            
            val adapter = recyclerView?.adapter as? ArticleAdapter
            // 核心修复：使用 Adapter 内部的当前真实列表来计算位置，彻底免疫倒序和过滤偏移
            val index = adapter?.getPositionForPost(target) ?: -1
            
            Timber.d("【跳转】触发定位，目标真实索引: $index")
            if (index != -1) {
                // 使用 post 方法，确保下一帧渲染时再执行滚动
                recyclerView?.post {
                    recyclerView?.smoothScrollToPosition(index)
                }
                pendingScrollPost = null
            }
        }
    }
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
            fun onLikeSuccess(event: LikeSuccessEvent) {
                recyclerView?.adapter?.let { adapter ->
                    if (adapter is ArticleAdapter) {
                        adapter.markAsLiked(event.url)
                    }
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
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .background(Color.White)
                                .wrapContentWidth() // 宽度自适应内容
                                .padding(vertical = 4.dp)
                        ) {
                            // 定义一个高度极小的行组件，彻底摆脱原生限制
                            @Composable
                            @OptIn(ExperimentalFoundationApi::class)
                            fun MiniMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, isActive: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp), // 小图标
                                        tint = if (isActive) Color(0xFFE53935) else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = text,
                                        fontSize = 13.sp, // 小字号
                                        color = if (isActive) Color(0xFFE53935) else Color.DarkGray,
                                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }

                            // === 第一组 ===
                            MiniMenuRow(Icons.Default.Person, if (showOpOnly) "显示全部" else "只看楼主", showOpOnly) {
                                showMenu = false
                                showOpOnly = !showOpOnly
                            }
                            MiniMenuRow(Icons.Default.Star, if (filterSpam) "关闭灌水" else "过滤灌水", filterSpam, 
                                onLongClick = {
                                    showMenu = false
                                    val density = context.resources.displayMetrics.density
                                    val dialog = android.app.AlertDialog.Builder(context).create()
                                    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

                                    val card = com.google.android.material.card.MaterialCardView(context).apply {
                                        radius = 16f * density
                                        setCardBackgroundColor(android.graphics.Color.WHITE)
                                        strokeWidth = 0
                                        elevation = 0f
                                    }

                                    val layout = android.widget.LinearLayout(context).apply {
                                        orientation = android.widget.LinearLayout.VERTICAL
                                        setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (8 * density).toInt())
                                    }

                                    layout.addView(android.widget.TextView(context).apply {
                                        text = "自定义过滤词"
                                        textSize = 15f
                                        setTextColor(android.graphics.Color.parseColor("#333333"))
                                        setTypeface(null, android.graphics.Typeface.BOLD)
                                        setPadding(0, 0, 0, (12 * density).toInt())
                                    })

                                    val inputCard = com.google.android.material.card.MaterialCardView(context).apply {
                                            radius = 8f * density
                                            setCardBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
                                            strokeWidth = (1 * density).toInt()
                                            strokeColor = android.graphics.Color.parseColor("#E0E0E0")
                                            elevation = 0f
                                        }
                                        val input = android.widget.EditText(context).apply {
                                            setText(customSpamKeywords.joinToString(","))
                                            setSelection(text.length)
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            hint = "输入过滤词（多个用逗号隔开）"
                                            inputType = android.text.InputType.TYPE_CLASS_TEXT
                                            val pad = (12 * density).toInt()
                                            setPadding(pad, pad, pad, pad)
                                        }
                                        inputCard.addView(input)
                                        layout.addView(inputCard)

                                    val btnLayout = android.widget.LinearLayout(context).apply {
                                        orientation = android.widget.LinearLayout.HORIZONTAL
                                        gravity = android.view.Gravity.END
                                        setPadding(0, (8 * density).toInt(), 0, 0)
                                    }

                                    btnLayout.addView(android.widget.TextView(context).apply {
                                        text = "取消"
                                        textSize = 14f
                                        setTextColor(android.graphics.Color.parseColor("#888888"))
                                        setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                                        setOnClickListener { dialog.dismiss() }
                                    })

                                    btnLayout.addView(android.widget.TextView(context).apply {
                                        text = "保存"
                                        textSize = 14f
                                        setTextColor(android.graphics.Color.parseColor("#E53935"))
                                        setTypeface(null, android.graphics.Typeface.BOLD)
                                        setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                                        setOnClickListener {
                                            val text = input.text.toString().trim()
                                            val keywords = if (text.isEmpty()) emptyList() else text.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                                            sp.edit().putStringSet("custom_spam_keywords", keywords.toSet()).apply()
                                            customSpamKeywords = keywords
                                            dialog.dismiss()
                                        }
                                    })

                                    layout.addView(btnLayout)
                                    card.addView(layout)
                                    dialog.setView(card)
                                    dialog.show()
                                    dialog.window?.setLayout((300 * density).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                                }, 
                                onClick = {
                                    showMenu = false
                                    filterSpam = !filterSpam
                                }
                            )
                            MiniMenuRow(Icons.Default.Search, if (searchKeyword.isNotBlank()) "清除搜索" else "搜索帖子", searchKeyword.isNotBlank()) {
                                showMenu = false
                                if (searchKeyword.isNotBlank()) {
                                    searchKeyword = ""
                                } else {
                                    val density = context.resources.displayMetrics.density
                                    val padding = (20 * density).toInt()
                                    val rootLayout = android.widget.LinearLayout(context).apply {
                                        orientation = android.widget.LinearLayout.VERTICAL
                                        setPadding(padding, padding / 2, padding, (10 * density).toInt())
                                    }
                                    rootLayout.addView(android.widget.TextView(context).apply {
                                        text = "搜索帖子内容"
                                        textSize = 16f
                                        setTypeface(null, android.graphics.Typeface.BOLD)
                                        setTextColor(android.graphics.Color.BLACK)
                                        setPadding(0, 0, 0, (16 * density).toInt())
                                    })
                                    val inputContainer = android.widget.FrameLayout(context).apply {
                                        val bg = android.graphics.drawable.GradientDrawable().apply {
                                            setColor(android.graphics.Color.parseColor("#F5F5F5"))
                                            cornerRadius = 8f * density
                                        }
                                        background = bg
                                    }
                                    val input = android.widget.EditText(context).apply {
                                        hint = "请输入关键字..."
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        setSingleLine(true)
                                        val p = (12 * density).toInt()
                                        setPadding(p, p, p, p)
                                        setTextColor(android.graphics.Color.BLACK)
                                    }
                                    inputContainer.addView(input)
                                    rootLayout.addView(inputContainer)
                                    val btnLayout = android.widget.LinearLayout(context).apply {
                                        orientation = android.widget.LinearLayout.HORIZONTAL
                                        gravity = android.view.Gravity.END
                                        setPadding(0, (16 * density).toInt(), 0, 0)
                                    }
                                    val btnCancel = android.widget.TextView(context).apply {
                                        text = "取消"
                                        textSize = 14f
                                        setTextColor(android.graphics.Color.parseColor("#888888"))
                                        setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                                    }
                                    val btnSearch = android.widget.TextView(context).apply {
                                        text = "搜索"
                                        textSize = 14f
                                        setTextColor(android.graphics.Color.parseColor("#E53935"))
                                        setTypeface(null, android.graphics.Typeface.BOLD)
                                        setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                                    }
                                    btnLayout.addView(btnCancel)
                                    btnLayout.addView(btnSearch)
                                    rootLayout.addView(btnLayout)
                                    val card = com.google.android.material.card.MaterialCardView(context).apply {
                                        radius = 16f * density
                                        setCardBackgroundColor(android.graphics.Color.WHITE)
                                        strokeWidth = 0
                                        elevation = 0f
                                        addView(rootLayout)
                                    }
                                    val dialog = android.app.AlertDialog.Builder(context).create()
                                    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                                    dialog.setView(card)
                                    dialog.show()
                                    dialog.window?.setLayout((300 * density).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                                    btnCancel.setOnClickListener { dialog.dismiss() }
                                    btnSearch.setOnClickListener {
                                        val keyword = input.text.toString().trim()
                                        if (keyword.isNotEmpty()) {
                                            searchKeyword = keyword
                                            dialog.dismiss()
                                        } else {
                                            android.widget.Toast.makeText(context, "请输入关键字", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            MiniMenuRow(Icons.Default.List, if (isReverse) "顺序显示" else "倒序显示", isReverse) {
                                showMenu = false
                                isReverse = !isReverse
                                prefs.edit().putBoolean("is_reverse", isReverse).apply()
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                thickness = 0.5.dp,
                                color = Color.LightGray.copy(alpha = 0.5f)
                            )

                            // === 第二组 ===
                            MiniMenuRow(Icons.Default.Refresh, "刷新帖子") {
                                showMenu = false
                                viewModel.fetchArticlePost(ArticleAdapterListener.Companion.FETCH_POST_INIT, null)
                            }
                            MiniMenuRow(Icons.Default.Share, "复制链接") {
                                showMenu = false
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Post Link", top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL + articleUrl)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            MiniMenuRow(Icons.Default.Search, "在浏览器打开") {
                                showMenu = false
                                WebViewActivity.startWebViewWith(WebsiteConstant.SERVER_BASE_URL + articleUrl, context)
                            }
                            MiniMenuRow(Icons.Default.KeyboardArrowDown, "提取下载链接") {
                                showMenu = false
                                val urls = viewModel.extractDownloadUrl()
                                if (!urls.isNullOrEmpty() && fragment.isAdded) {
                                    newDownloadLinkDialog(urls).show(fragment.parentFragmentManager, "DownloadLinkDialog")
                                } else {
                                    android.widget.Toast.makeText(context, R.string.download_link_not_found, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            MiniMenuRow(if (isFavorited) Icons.Filled.Favorite else Icons.Default.FavoriteBorder, if (isFavorited) "取消收藏" else "添加收藏", isFavorited) {
                                showMenu = false
                                viewModel.toggleFavorite()
                            }
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
                // 核心修复：在搜索或过滤状态下，彻底隐藏所有悬浮按钮（包括回复按钮）
                if (searchKeyword.isNotBlank() || filterSpam || showOpOnly) {
                    return@Scaffold
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showBackToTop && searchKeyword.isBlank() && !filterSpam && !showOpOnly) {
                        FloatingActionButton(
                            onClick = { recyclerView?.smoothScrollToPosition(0) },
                            containerColor = Color.White,
                            contentColor = Color.Gray,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "回到顶部", modifier = Modifier.size(20.dp))
                        }
                    }
                    
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
                            isReverse = isReverse,
                            showOpOnly = showOpOnly,
                            filterSpam = filterSpam,
                            customSpamKeywords = customSpamKeywords,
                            searchKeyword = searchKeyword,
                            onScrollStateChanged = { show -> showBackToTop = show },
                            onPostClick = { post ->
                                Timber.d("【跳转】卡片被点击，开始清空搜索条件并跳转")
                                searchKeyword = ""
                                filterSpam = false
                                showOpOnly = false
                                pendingScrollPost = post
                            },
                            onReady = { rv -> recyclerView = rv }
                        )
            }
        }
    }
}
