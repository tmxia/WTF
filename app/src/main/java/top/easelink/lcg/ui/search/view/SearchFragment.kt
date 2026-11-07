package top.easelink.lcg.ui.search.view

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
import androidx.compose.ui.text.input.ImeAction
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: LCGSearchViewModel, onNavigate: (Fragment) -> Unit) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchResults by viewModel.searchResults.observeAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.observeAsState(initial = false)
    val totalResult by viewModel.totalResult.observeAsState(initial = null)
    
    var keyword by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        // 顶部搜索栏
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("搜索帖子、资源...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFE53935),
                unfocusedBorderColor = Color.LightGray,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (keyword.isNotBlank()) {
                    viewModel.setKeyword(keyword)
                    keyboardController?.hide()
                } else {
                    Toast.makeText(context, "请输入关键字", Toast.LENGTH_SHORT).show()
                }
            })
        )

        // 结果区
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFE53935))
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

@Composable
fun SearchResultCard(item: LCGSearchResultItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.Black, maxLines = 2)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = item.content, fontSize = 13.sp, color = Color.Gray, maxLines = 3)
        }
    }
}
