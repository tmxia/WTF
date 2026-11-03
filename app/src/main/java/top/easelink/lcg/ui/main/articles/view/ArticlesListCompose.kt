package top.easelink.lcg.ui.main.articles.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ForumThread

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ForumArticlesScreen(
    title: String,
    tabs: List<ForumThread>,
    articles: List<Article>,
    onBackClick: () -> Unit,
    onTabSelected: (String) -> Unit,
    onArticleClick: (String) -> Unit,
    onLoadMore: () -> Unit // 核心修复：明确声明此参数
) {
    val displayTabs = remember(tabs) {
        val list = mutableListOf("全部")
        tabs.filter { it.threadName != "全部" }.forEach { list.add(it.threadName) }
        list
    }
    
    val pagerState = rememberPagerState(pageCount = { displayTabs.size })
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    
    // 触底加载更多
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= articles.size - 3 && articles.isNotEmpty()) {
                    onLoadMore()
                }
            }
    }

    // 返回顶部按钮显隐
    val showBackToTop by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 30 }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > 0) {
            val index = pagerState.currentPage - 1
            val url = if (index < tabs.filter { it.threadName != "全部" }.size) {
                tabs.filter { it.threadName != "全部" }[index].threadUrl
            } else { "" }
            onTabSelected(url)
        } else {
            onTabSelected("")
        }
        lazyListState.scrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.background(Color.White)) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )

                if (displayTabs.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 16.dp,
                        containerColor = Color.White,
                        contentColor = Color(0xFFE65100),
                        divider = { HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray) },
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]).fillMaxWidth(),
                                height = 3.dp,
                                color = Color(0xFFE65100)
                            )
                        }
                    ) {
                        displayTabs.forEachIndexed { index, tabTitle ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = {
                                    Text(
                                        text = tabTitle,
                                        fontSize = 15.sp,
                                        color = if (pagerState.currentPage == index) Color(0xFFE65100) else Color(0xFF888888),
                                        fontWeight = if (pagerState.currentPage == index) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                beyondBoundsPageCount = 1
            ) { page ->
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    items(articles) { article ->
                        ArticleCard(article, onArticleClick)
                    }
                    item {
                        if (articles.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFFE65100))
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showBackToTop,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            FloatingActionButton(
                onClick = { scope.launch { lazyListState.animateScrollToItem(0) } },
                containerColor = Color.White,
                contentColor = Color(0xFFE65100),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "回到顶部")
            }
        }
    }
}

@Composable
fun ArticleCard(article: Article, onItemClick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().clickable { onItemClick(article.url) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = article.author, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
                if (article.origin.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ForumChip(article.origin)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = article.date, fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Color(0xFF999999))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = article.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "${article.reply} / ${article.view}", fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color(0xFF999999))
        }
    }
}

@Composable
fun ForumChip(name: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Transparent)
            .border(0.1.dp, Color(0xFFA0A0A0), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = name, fontSize = 10.sp, fontWeight = FontWeight.Normal, color = Color(0xFF808080), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
