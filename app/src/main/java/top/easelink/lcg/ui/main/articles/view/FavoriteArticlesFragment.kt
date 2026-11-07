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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import top.easelink.framework.topbase.BaseFragment
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.articles.viewmodel.FavoriteArticlesViewModel
import top.easelink.lcg.ui.webview.view.WebViewActivity
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL

class FavoriteArticlesFragment : BaseFragment<FragmentFavoriteArticlesBinding, FavoriteArticlesViewModel>() {
    // BaseFragment 需要保留 getLayoutId，但我们现在不依赖它渲染内容了
    override fun getLayoutId(): Int = R.layout.fragment_favorite_articles
    private lateinit var mViewModel: FavoriteArticlesViewModel

    override fun initViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentFavoriteArticlesBinding {
        return FragmentFavoriteArticlesBinding.inflate(inflater, container, false)
    }

    override fun getViewModel(): FavoriteArticlesViewModel {
        return ViewModelProvider(this)[FavoriteArticlesViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mViewModel = getViewModel()
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel.fetchArticles(top.easelink.lcg.ui.main.articles.viewmodel.ArticleFetcher.FetchType.FETCH_INIT) {}
        
        (view as ComposeView).setContent {
            val articles by mViewModel.articles.observeAsState(initial = emptyList())
            val isLoading by mViewModel.isLoading.observeAsState(initial = false)
            
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
                            IconButton(onClick = { mViewModel.removeAllFavorites() }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = Color.Black)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                }
                
                if (isLoading && articles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE53935))
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
                                        Text(text = article.date ?: "", fontSize = 12.sp, color = Color.Gray)
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