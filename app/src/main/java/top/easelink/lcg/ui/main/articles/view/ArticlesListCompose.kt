package top.easelink.lcg.ui.main.articles.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.easelink.lcg.ui.main.source.model.Article

@Composable
fun ArticlesListCompose(
    articles: List<Article>,
    onItemClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        items(articles) { article ->
            ArticleCard(article, onItemClick)
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
            .padding(horizontal = 6.dp, vertical = 2.5.dp) // MTF 标准外边距
            .clickable { onItemClick(article.url) }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp)) { // MTF 标准内边距
            // 第一行：作者、标签、时间
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
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                )
            }
            
            Spacer(modifier = Modifier.height(9.dp))
            
            // 标题
            Text(
                text = article.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(11.dp))
            
            // 底部数据
            Text(
                text = "${article.reply} / ${article.view}",
                fontSize = 13.sp,
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
            .border(0.1.dp, Color(0xFFA0A0A0), RoundedCornerShape(50)) // 极细边框
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = name,
            fontSize = 10.sp,
            color = Color(0xFF808080),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
