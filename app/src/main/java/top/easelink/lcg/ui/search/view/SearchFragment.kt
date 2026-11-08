package top.easelink.lcg.ui.search.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.framework.topbase.TopFragment
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.article.view.ArticleFragment
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import top.easelink.lcg.ui.search.model.LCGSearchResultItem
import top.easelink.lcg.ui.search.viewmodel.LCGSearchViewModel
import top.easelink.lcg.ui.webview.view.WebViewActivity
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import top.easelink.lcg.utils.showMessage

class SearchFragment : TopFragment(), ControllableFragment {
    private lateinit var viewModel: LCGSearchViewModel

    override fun isControllable(): Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[LCGSearchViewModel::class.java]

        (view as ComposeView).setContent {
            SearchScreen(
                viewModel = viewModel,
                onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                onNavigate = { fragment ->
                    activity?.supportFragmentManager?.let { fm ->
                        top.easelink.framework.utils.addFragmentInActivity(fm, fragment, R.id.clRootView)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(viewModel: LCGSearchViewModel, onBack: () -> Unit, onNavigate: (Fragment) -> Unit) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchResults by viewModel.searchResults.observeAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.observeAsState(initial = false)
    val isLoadingMore by viewModel.isLoadingMore.observeAsState(initial = false)
    val totalResult by viewModel.totalResult.observeAsState(initial = null)
    val forumList by viewModel.forumList.observeAsState(initial = emptyList())
    val selectedFidName by viewModel.selectedFidName.observeAsState(initial = "全部板块")

    var keyword by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }
    var showBoardDialog by remember { mutableStateOf(false) }
    
    val prefs = remember { context.getSharedPreferences("wtf_search_prefs", Context.MODE_PRIVATE) }
    var historyList by remember { mutableStateOf(loadHistory(prefs)) }
    val hotSearchWords = listOf("微信", "逆向破解", "Magisk", "Xposed", "Dex修复", "汉化教程", "自动化脚本", "Arsc")
    val primaryColor = Color(0xFFE53935)
    val bgColor = Color(0xFFF5F5F5)

    LaunchedEffect(Unit) { viewModel.loadForums() } // 预加载板块列表

    fun performSearch(query: String) {
        if (query.isNotBlank()) {
            keyword = query
            hasSearched = true
            keyboardController?.hide()
            viewModel.setKeyword(query)
            val newHistory = (listOf(query) + historyList.filterNot { it == query }).take(10)
            historyList = newHistory
            saveHistory(prefs, newHistory)
        } else {
            Toast.makeText(context, "请输入关键字", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // 沉浸式 TopBar
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(top = 40.dp, bottom = 10.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.Black)
            }
            OutlinedTextField(
                value = keyword,
                onValueChange = { 
                    keyword = it
                    if (it.isEmpty()) hasSearched = false 
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text("搜索帖子...", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (keyword.isNotEmpty()) {
                        IconButton(onClick = { keyword = ""; hasSearched = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = bgColor,
                    unfocusedContainerColor = bgColor
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch(keyword) })
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "搜索", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryColor, 
                modifier = Modifier.clickable { performSearch(keyword) }
            )
        }

        // 板块过滤栏
        Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(bgColor, RoundedCornerShape(12.dp))
                    .clickable { showBoardDialog = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("范围：$selectedFidName", fontSize = 12.sp, color = Color.DarkGray)
                Text(" ▾", fontSize = 10.sp, color = Color.DarkGray)
            }
        }

        // 内容区
        Box(modifier = Modifier.fillMaxSize()) {
            if (!hasSearched) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    if (historyList.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("搜索历史", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                            Text("清空", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.clickable { historyList = emptyList(); saveHistory(prefs, emptyList()) })
                        }
                        FlowRow(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            historyList.forEach { tag -> SearchChip(text = tag, primaryColor = primaryColor) { performSearch(tag) } }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("热门搜索", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primaryColor)
                    }
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        hotSearchWords.forEach { tag -> SearchChip(text = tag, primaryColor = primaryColor, isHot = true) { performSearch(tag) } }
                    }
                }
            } else {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = primaryColor)
                } else if (searchResults.isEmpty()) {
                    if (!totalResult.isNullOrEmpty()) Text("未找到相关内容", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                } else {
                    val listState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val total = listState.layoutInfo.totalItemsCount
                            total > 0 && lastVisible >= total - 3
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) viewModel.loadNextPage()
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Text(totalResult ?: "", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp)) }
                        items(searchResults) { item ->
                            SearchResultCard(item) {
                                val threadRegex = Regex("thread-[0-9]+-[0-9]+-[0-9]+.html$", RegexOption.IGNORE_CASE)
                                if (threadRegex.containsMatchIn(item.url) || item.url.startsWith("forum.php")) {
                                    onNavigate(ArticleFragment.newInstance(item.url))
                                } else if (item.url.startsWith("http") || item.url.startsWith(SERVER_BASE_URL)) {
                                    WebViewActivity.startWebViewWith(item.url, context)
                                } else {
                                    showMessage(R.string.general_error)
                                }
                            }
                        }
                        if (isLoadingMore) {
                            item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = primaryColor) } }
                        }
                    }
                }
            }
        }
    }

    // 板块选择弹窗（图3效果）
    if (showBoardDialog) {
        Dialog(onDismissRequest = { showBoardDialog = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("选择板块", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showBoardDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    BoardChip("全部板块", selectedFidName == "全部板块") {
                        viewModel.selectFid(null, "全部板块")
                        showBoardDialog = false
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    forumList.forEach { forum ->
                        BoardChip(forum.title, selectedFidName == forum.title) {
                            viewModel.selectFid(forum.fid, forum.title)
                            showBoardDialog = false
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BoardChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFFE53935).copy(alpha = 0.1f) else Color(0xFFF5F5F5)
    val fg = if (selected) Color(0xFFE53935) else Color.DarkGray
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg).clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(name, fontSize = 14.sp, color = fg)
    }
}

@Composable
fun SearchChip(text: String, primaryColor: Color, isHot: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier.background(if (isHot) primaryColor.copy(alpha = 0.1f) else Color.White, RoundedCornerShape(16.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = text, fontSize = 13.sp, color = if (isHot) primaryColor else Color.DarkGray)
    }
}

@Composable
fun SearchResultCard(item: LCGSearchResultItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = fromHtml(item.title), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = fromHtml(item.content), fontSize = 13.sp, color = Color.Gray, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun loadHistory(prefs: android.content.SharedPreferences): List<String> {
    val raw = prefs.getString("history", "") ?: ""
    return if (raw.isBlank()) emptyList() else raw.split("|||")
}
private fun saveHistory(prefs: android.content.SharedPreferences, list: List<String>) {
    prefs.edit().putString("history", list.joinToString("|||")).apply()
}
