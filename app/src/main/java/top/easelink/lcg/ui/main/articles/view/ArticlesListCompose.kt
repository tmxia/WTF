package top.easelink.lcg.ui.main.articles.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.easelink.lcg.ui.main.source.model.Article
import top.easelink.lcg.ui.main.source.model.ForumThread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumArticlesScreen(
    title: String,
    tabs: List<ForumThread>,
    articles: List<Article>,
    onBackClick: () -> Unit,
    onTabSelected: (String) -> Unit,
    onArticleClick: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }

    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        
        Column(modifier = Modifier.background(Color.White)) {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0), 
                title = {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )

            if (tabs.isNotEmpty()) {
                ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 16.dp,
                        containerColor = Color.White,
                        contentColor = Color(0xFFE65100), // 统一主题红
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                height = 3.dp,
                                color = Color(0xFFE65100)
                            )
                        },
                        divider = { HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray) }
                    ) {
                    val displayTabs = mutableListOf("全部")
                    tabs.filter { it.threadName != "全部" }.forEach {
                        displayTabs.add(it.threadName)
                    }

                    displayTabs.forEachIndexed { index, tabTitle ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                                val url = if (index == 0) "" else tabs.filter { it.threadName != "全部" }[index - 1].threadUrl
                                onTabSelected(url)
                            },
                            text = {
                                Text(
                                    text = tabTitle,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }

        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), 
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            items(articles) { article ->
                ArticleCard(article, onArticleClick)
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(article.url) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                
                Text(
                    text = article.author,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )

                if (article.origin.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ForumChip(article.origin)
                }

                Spacer(modifier = Modifier.weight(1f))

                
                Text(
                    text = article.date,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF999999)
                )
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

            
            Text(
                text = "${article.reply} / ${article.view}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF999999)
            )
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
        Text(
            text = name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF808080),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
