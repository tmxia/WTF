@file:OptIn(ExperimentalMaterial3Api::class)
package top.easelink.lcg.ui.main.articles.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.lifecycle.ViewModelProvider
import top.easelink.framework.base.BaseFragment
import top.easelink.lcg.R
import top.easelink.lcg.databinding.FragmentFavoriteArticlesBinding
import top.easelink.lcg.ui.main.articles.viewmodel.FavoriteArticlesViewModel
import top.easelink.lcg.ui.webview.view.WebViewActivity
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL
import top.easelink.lcg.utils.WebsiteConstant.GET_FAVORITE_QUERY

class FavoriteArticlesFragment : BaseFragment<FragmentFavoriteArticlesBinding, FavoriteArticlesViewModel>() {
    private var _binding: FragmentFavoriteArticlesBinding? = null
    private val binding get() = _binding!!

    override fun getLayoutId(): Int = R.layout.fragment_favorite_articles

    override fun initViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentFavoriteArticlesBinding {
        return FragmentFavoriteArticlesBinding.inflate(inflater, container, false).also { _binding = it }
    }

    override fun getViewModel(): FavoriteArticlesViewModel {
        return ViewModelProvider(this)[FavoriteArticlesViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        
        viewModel.fetchArticles(top.easelink.lcg.ui.main.articles.viewmodel.ArticleFetcher.FetchType.FETCH_INIT) {}
        
        (view as ComposeView).setContent {
            val articles by viewModel.articles.observeAsState(initial = emptyList())
            val isLoading by viewModel.isLoading.observeAsState(initial = false)
            var showMenu by remember { mutableStateOf(false) }
            val context = LocalContext.current

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    TopAppBar(
                        title = { Text("我的收藏", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
                        navigationIcon = {
                            IconButton(onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.Black)
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "菜单", tint = Color.Black)
                                }
                                
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("清空收藏", fontSize = 14.sp) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.removeAllFavorites()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("同步收藏", fontSize = 14.sp) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.syncFavorites()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("管理收藏", fontSize = 14.sp) },
                                        onClick = {
                                            showMenu = false
                                            WebViewActivity.startWebViewWith(SERVER_BASE_URL + GET_FAVORITE_QUERY, context)
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                }
                
                if (isLoading && articles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFFE53935),
                            strokeWidth = 3.dp
                        )
                    }
                } else if (articles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("目前没有内容可供展示", color = Color.Gray)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(articles) { article ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    WebViewActivity.startWebViewWith(SERVER_BASE_URL + article.url, requireContext())
                                }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = article.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = article.author ?: "", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
