package top.easelink.lcg.ui.search.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.framework.topbase.TopFragment
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.article.view.ArticleFragment
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    
    val defaultHotWords = listOf("微信", "逆向破解", "Magisk", "Xposed", "Dex修复", "汉化教程", "自动化脚本", "Arsc")
    var hotSearchWords by remember { mutableStateOf(loadHotWords(prefs, defaultHotWords)) }
    var showCustomHotDialog by remember { mutableStateOf(false) }
    var editingHotIndex by remember { mutableStateOf(-1) }
    var editingHotText by remember { mutableStateOf("") }

    val chipColors = listOf(
        Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
        Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00ACC1)
    )
    
    val primaryColor = Color(0xFFE53935) 
    val bgColor = Color(0xFFF5F5F5)      
    val inputBgColor = Color(0xFFF2F3F5) 

    // 拦截手势/物理返回键
    BackHandler(enabled = true) {
        if (keyword.isNotEmpty()) {
            keyword = ""
            hasSearched = false
            keyboardController?.hide()
        } else {
            onBack()
        }
    }

    LaunchedEffect(Unit) { viewModel.loadForums() }

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
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(top = 40.dp, bottom = 10.dp, start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.Black, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            
            // 搜索输入框
            Row(
                modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(inputBgColor).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = keyword,
                    onValueChange = { 
                        keyword = it
                        if (it.isEmpty()) hasSearched = false 
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                    cursorBrush = SolidColor(primaryColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch(keyword) }),
                    decorationBox = { innerTextField: @Composable () -> Unit ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (keyword.isEmpty()) {
                                Text("输入关键词搜索帖子...", color = Color.Gray, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    }
                )
                if (keyword.isNotEmpty()) {
                    Box(
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).clickable { keyword = ""; hasSearched = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            
            // 动态搜索按钮：有字亮起，无字灰色
            val isButtonActive = keyword.isNotBlank()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = isButtonActive) { performSearch(keyword) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "搜索", 
                    fontSize = 15.sp, 
                    fontWeight = FontWeight.Medium, 
                    color = if (isButtonActive) primaryColor else Color.Gray
                )
            }
        }

        // 板块过滤栏
        Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { showBoardDialog = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("范围：$selectedFidName", fontSize = 12.sp, color = Color.DarkGray)
                Text(" ▾", fontSize = 10.sp, color = Color.DarkGray)
            }
        }

        // 内容区
        Box(modifier = Modifier.fillMaxSize()) {
            if (!hasSearched) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (historyList.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("搜索历史", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { 
                                    historyList = emptyList()
                                    saveHistory(prefs, emptyList()) 
                                }.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("清空", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        FlowRow(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            historyList.forEach { tag -> 
                                SearchChip(
                                    text = tag, 
                                    chipBgColor = Color.White,
                                    onClick = { performSearch(tag) },
                                    onLongClick = {
                                        val updated = historyList.filterNot { it == tag }
                                        historyList = updated
                                        saveHistory(prefs, updated)
                                        Toast.makeText(context, "已删除「$tag」", Toast.LENGTH_SHORT).show()
                                    }
                                ) 
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("热门搜索 (长按可自定义)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primaryColor)
                    }
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        hotSearchWords.forEachIndexed { index, tag -> 
                            val color = chipColors[index % chipColors.size]
                            SearchChip(
                                text = tag, 
                                chipBgColor = color.copy(alpha = 0.1f), 
                                isHot = true, 
                                textColor = color, 
                                onClick = { performSearch(tag) },
                                onLongClick = {
                                    editingHotIndex = index
                                    editingHotText = tag
                                    showCustomHotDialog = true
                                }
                            ) 
                        }
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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

    // 板块选择弹窗
    if (showBoardDialog) {
        Dialog(
            onDismissRequest = { showBoardDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("选择板块", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable { showBoardDialog = false }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (selectedFidName == "全部板块") primaryColor.copy(alpha = 0.1f) else bgColor)
                        .clickable { viewModel.selectFid(null, "全部板块"); showBoardDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text("全部板块", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (selectedFidName == "全部板块") primaryColor else Color.DarkGray)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("常用板块", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 10.dp))
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    forumList.forEach { forum ->
                        val isSelected = selectedFidName == forum.title
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) primaryColor.copy(alpha = 0.1f) else bgColor)
                                .clickable { viewModel.selectFid(forum.fid, forum.title); showBoardDialog = false }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(forum.title, fontSize = 14.sp, color = if (isSelected) primaryColor else Color.Black, modifier = Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // 自定义热门搜索词弹窗
    if (showCustomHotDialog) {
        Dialog(onDismissRequest = { showCustomHotDialog = false }) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(20.dp)) {
                Text("自定义热门搜索词", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = editingHotText,
                    onValueChange = { editingHotText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("取消", modifier = Modifier.clickable { showCustomHotDialog = false }.padding(8.dp), color = Color.Gray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("保存", modifier = Modifier.clickable {
                        if (editingHotText.isNotBlank() && editingHotIndex >= 0) {
                            val updatedList = hotSearchWords.toMutableList()
                            updatedList[editingHotIndex] = editingHotText.trim()
                            hotSearchWords = updatedList
                            saveHotWords(prefs, updatedList)
                        }
                        showCustomHotDialog = false
                    }.padding(8.dp), color = primaryColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchChip(
    text: String, 
    chipBgColor: Color, 
    isHot: Boolean = false, 
    textColor: Color = Color.DarkGray, 
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(chipBgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = text, fontSize = 13.sp, color = textColor)
    }
}

@Composable
fun SearchResultCard(item: LCGSearchResultItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp), 
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), 
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = parseHtmlHighlight(item.title), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = parseHtmlHighlight(item.content), fontSize = 13.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(10.dp))
            
            // 底部常规信息：板块、作者、时间、查看/回复
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 板块（小胶囊背景）
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF2F3F5)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(item.forum, fontSize = 11.sp, color = Color.DarkGray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(item.author, fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.weight(1f))
                Text("${item.date}  |  ${item.replyView}", fontSize = 11.sp, color = Color.Gray)
            }
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

private fun loadHotWords(prefs: android.content.SharedPreferences, defaults: List<String>): List<String> {
    val raw = prefs.getString("hot_words", "") ?: ""
    return if (raw.isBlank()) defaults else raw.split("|||")
}
private fun saveHotWords(prefs: android.content.SharedPreferences, list: List<String>) {
    prefs.edit().putString("hot_words", list.joinToString("|||")).apply()
}

// 轻量级 HTML 解析器，完美替代高版本 Compose 的 fromHtml，并保留粗体和红色高亮
fun parseHtmlHighlight(html: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = html.split(Regex("(<font color=\"#ff0000\">|</font>|<strong>|</strong>)", RegexOption.IGNORE_CASE))
        var isRed = false
        var isBold = false
        for (part in parts) {
            when (part.lowercase()) {
                "<font color=\"#ff0000\">" -> isRed = true
                "</font>" -> isRed = false
                "<strong>" -> isBold = true
                "</strong>" -> isBold = false
                else -> {
                    if (part.isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                color = if (isRed) Color(0xFFE53935) else Color.Unspecified,
                                fontWeight = if (isBold) FontWeight.Bold else null
                            )
                        ) {
                            append(android.text.Html.fromHtml(part).toString())
                        }
                    }
                }
            }
        }
    }
}
