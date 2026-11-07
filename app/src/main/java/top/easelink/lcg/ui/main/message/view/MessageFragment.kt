package top.easelink.lcg.ui.main.message.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.framework.topbase.TopFragment
import top.easelink.lcg.account.AccountManager
import top.easelink.lcg.ui.main.message.viewmodel.ConversationListViewModel
import top.easelink.lcg.ui.main.message.viewmodel.NotificationViewModel
import top.easelink.lcg.ui.main.model.BaseNotification
import top.easelink.lcg.ui.main.model.Conversation
import top.easelink.lcg.ui.webview.view.WebViewActivity
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL

class MessageFragment : TopFragment(), ControllableFragment {

    private lateinit var notificationViewModel: NotificationViewModel
    private lateinit var conversationViewModel: ConversationListViewModel

    override fun isControllable(): Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        notificationViewModel = ViewModelProvider(this)[NotificationViewModel::class.java]
        conversationViewModel = ViewModelProvider(this)[ConversationListViewModel::class.java]
        val context = requireContext()

        (view as ComposeView).setContent {
            val isLoggedIn by AccountManager.isLoggedIn.observeAsState(initial = false)
            val notifications by notificationViewModel.notifications.observeAsState()
            val conversations by conversationViewModel.conversations.observeAsState()
            val isNotifLoading by notificationViewModel.isLoading.observeAsState(initial = false)
            val isConvLoading by conversationViewModel.isLoading.observeAsState(initial = false)

            var selectedTab by remember { mutableIntStateOf(0) }

            if (!isLoggedIn) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) {
                    Text(text = "请先登录", color = Color.Gray)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
                    // 核心修复：在顶部栏外层包裹一个 Column，强制填充白色，并加上状态栏避让
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .statusBarsPadding() // 完美避开系统状态栏
                    ) {
                        // 顶部导航栏 (Material3 标准写法)
                        TopAppBar(
                            title = { Text("我的信息", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
                            navigationIcon = {
                                IconButton(onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.Black)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                            windowInsets = WindowInsets(0, 0, 0, 0) // 取消内部自带的 Insets 避让，防止双重留白
                        )

                    // Tab 栏 (完美复刻“我的帖子”的 ScrollableTabRow)
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 16.dp,
                        containerColor = Color.White,
                        contentColor = Color(0xFFE53935),
                        divider = { HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray) },
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[selectedTab])
                                    .fillMaxWidth(),
                                height = 3.dp,
                                color = Color(0xFFE53935) // 使用与你主题一致的红色
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { 
                                Text(
                                    text = "通知",
                                    fontSize = 15.sp,
                                    color = if (selectedTab == 0) Color(0xFFE53935) else Color(0xFF888888),
                                    fontWeight = if (selectedTab == 0) FontWeight.Medium else FontWeight.Normal
                                ) 
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { 
                                Text(
                                    text = "私信",
                                    fontSize = 15.sp,
                                    color = if (selectedTab == 1) Color(0xFFE53935) else Color(0xFF888888),
                                    fontWeight = if (selectedTab == 1) FontWeight.Medium else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                    } // 闭合包裹 TopAppBar 和 ScrollableTabRow 的白色 Column
                    
                    // 内容区
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        if (selectedTab == 0) {
                            if (isNotifLoading && notifications == null) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFE53935))
                            } else {
                                NotificationList(notifications?.notifications ?: emptyList())
                            }
                        } else {
                            if (isConvLoading && conversations == null) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFE53935))
                            } else {
                                ConversationList(conversations ?: emptyList(), context)
                            }
                        }
                    }
                }
            }
            
            // 初始化数据
            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    notificationViewModel.fetchNotifications()
                    conversationViewModel.fetchConversations()
                }
            }
        }
    }
}

@Composable
fun NotificationList(list: List<BaseNotification>) {
    if (list.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("目前没有内容可供展示", color = Color.Gray)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list) { item ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp)) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(item.avatar).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = item.dateTime, fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = item.content, fontSize = 14.sp, color = Color.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationList(list: List<Conversation>, context: android.content.Context) {
    if (list.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("目前没有内容可供展示", color = Color.Gray)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list) { item ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        WebViewActivity.startWebViewWith(SERVER_BASE_URL + item.replyUrl, context)
                    }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(item.avatar).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.username, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = item.lastMessage, fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(text = item.lastMessageDateTime, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
