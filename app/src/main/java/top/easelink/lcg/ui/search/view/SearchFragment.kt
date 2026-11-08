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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun SearchScreen(viewModel: LCGSearchViewModel, onNavigate: (Fragment) -> Unit) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchResults by viewModel.searchResults.observeAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.observeAsState(initial = false)
    val totalResult by viewModel.totalResult.observeAsState(initial = null)

    var keyword by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }
    
    // 加载本地搜索历史
    val prefs = remember { context.getSharedPreferences("wtf_search_prefs", Context.MODE_PRIVATE) }
    var historyList by remember { mutableStateOf(loadHistory(prefs)) }
    
    // 热门搜索死数据
    val hotSearchWords = listOf("微信", "逆向破解", "Magisk", "Xposed", "Dex修复", "汉化教程", "自动化脚本", "Arsc")

    val primaryColor = Color(0xFFE53935) // WTF 红色主题
    val bgColor = Color(0xFFF5F5F5)

    fun performSearch(query: String) {
        if (query.isNotBlank()) {
            keyword = query
            hasSearched = true
            keyboardController?.hide()
            viewModel.setKeyword(query)
            
            // 存入历史记录
            val newHistory = (listOf(query) + historyList.filterNot { it == query }).take(10)
            historyList = newHistory
            saveHistory(prefs, newHistory)
        } else {
            Toast.makeText(context, "请输入关键字", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // 顶部搜索栏
        OutlinedTextField(
            value = keyword,
            onValueChange = { 
                keyword = it
                if (it.isEmpty()) hasSearched = false // 清空输入框恢复历史页面
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("搜索帖子、资源...", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = { 
                        keyword = "" 
                        hasSearched = false 
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { performSearch(keyword) })
        )

        // 内容区
        Box(modifier = Modifier.fillMaxSize()) {
            if (!hasSearched) {
                // ====== 未搜索状态：显示历史和热门 ======
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    if (historyList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("搜索历史", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                            Text("清空", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.clickable {
                                historyList = emptyList()
                                saveHistory(prefs, emptyList())
                            })
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            historyList.forEach { tag ->
                                SearchChip(text = tag, primaryColor = primaryColor) { performSearch(tag) }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("热门搜索", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primaryColor)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hotSearchWords.forEach { tag ->
                            SearchChip(text = tag, primaryColor = primaryColor, isHot = true) { performSearch(tag) }
                        }
                    }
                }
            } else {
                // ====== 已搜索状态：显示结果 ======
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = primaryColor)
                } else if (searchResults.isEmpty()) {
                    if (!totalResult.isNullOrEmpty()) {
                        Text("未找到相关内容", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(totalResult ?: "", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
                        }
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
                    }
                }
            }
        }
    }
}

@Composable
fun SearchChip(text: String, primaryColor: Color, isHot: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (isHot) primaryColor.copy(alpha = 0.1f) else Color.White,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text, 
            fontSize = 13.sp, 
            color = if (isHot) primaryColor else Color.DarkGray
        )
    }
}

@Composable
fun SearchResultCard(item: LCGSearchResultItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 核心修复：使用 fromHtml 完美渲染 <strong> 和 <font> 标签
            Text(
                text = fromHtml(item.title), 
                fontSize = 15.sp, 
                fontWeight = FontWeight.Medium, 
                color = Color.Black, 
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fromHtml(item.content), 
                fontSize = 13.sp, 
                color = Color.Gray, 
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
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
