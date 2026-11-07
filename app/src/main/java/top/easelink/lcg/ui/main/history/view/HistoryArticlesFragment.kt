@file:OptIn(ExperimentalMaterial3Api::class)
package top.easelink.lcg.ui.main.history.view

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.easelink.framework.threadpool.IOPool
import top.easelink.framework.topbase.ControllableFragment
import top.easelink.framework.topbase.TopFragment
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.history.model.HistoryModel
import top.easelink.lcg.ui.main.source.local.ArticlesDatabase
import top.easelink.lcg.ui.webview.view.WebViewActivity
import top.easelink.lcg.utils.WebsiteConstant.SERVER_BASE_URL

class HistoryArticlesFragment : TopFragment(), ControllableFragment {

    override fun isControllable(): Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        (view as ComposeView).setContent {
            var historyList by remember { mutableStateOf<List<HistoryModel>>(emptyList()) }

            LaunchedEffect(Unit) {
                ArticlesDatabase.getInstance().articlesDao().getHistories().observe(viewLifecycleOwner) { list ->
                    historyList = list.map { HistoryModel(url = it.url, author = it.author, title = it.title, timeStamp = it.timestamp) }
                }
            }

            Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    TopAppBar(
                        title = { Text("阅读记录", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
                        navigationIcon = {
                            IconButton(onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.Black)
                            }
                        },
                        actions = {
                            IconButton(onClick = { 
                                lifecycleScope.launch(IOPool) {
                                    ArticlesDatabase.getInstance().articlesDao().deleteHistories()
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "清空", tint = Color.Black)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                }

                if (historyList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("目前没有内容可供展示", color = Color.Gray)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(historyList) { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    WebViewActivity.startWebViewWith(SERVER_BASE_URL + item.url, requireContext())
                                }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = item.author, fontSize = 12.sp, color = Color.Gray)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "刚刚", fontSize = 12.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "删除",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp).clickable { 
                                                    lifecycleScope.launch(IOPool) {
                                                        ArticlesDatabase.getInstance().articlesDao().deleteHistory(item.url)
                                                    }
                                                }
                                            )
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

    companion object {
        fun newInstance(): HistoryArticlesFragment {
            return HistoryArticlesFragment()
        }
    }
}