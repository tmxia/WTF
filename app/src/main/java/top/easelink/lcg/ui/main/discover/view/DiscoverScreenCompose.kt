@file:OptIn(ExperimentalLayoutApi::class)

package top.easelink.lcg.ui.main.discover.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.discover.model.DiscoverModel
import top.easelink.lcg.ui.main.discover.model.ForumListModel
import top.easelink.lcg.ui.main.discover.model.RankListModel
import top.easelink.lcg.ui.main.discover.model.RankModel

@Composable
fun DiscoverScreenCompose(
    models: List<DiscoverModel>,
    onForumClick: (String, String) -> Unit,
    onMoreForumsClick: () -> Unit,
    onRankClick: (String) -> Unit
) {
    if (models.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "暂无数据，请下拉刷新", color = Color.Gray)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        items(models) { model ->
            when (model) {
                is ForumListModel -> ForumNavigationSection(model, onForumClick, onMoreForumsClick)
                is RankListModel -> RankListSection(model, onRankClick)
            }
        }
    }
}

@Composable
fun ForumNavigationSection(
    model: ForumListModel,
    onForumClick: (String, String) -> Unit,
    onMoreClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "常用板块导航",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "右滑查看更多 >",
                    fontSize = 11.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.clickable { onMoreClick() }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 使用 LazyRow 实现横向滑动折叠效果
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(model.forumList.take(8)) { forum ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(60.dp)
                            .clickable { onForumClick(forum.title, forum.url) }
                    ) {
                        Icon(
                            painter = painterResource(id = forum.drawableRes),
                            contentDescription = forum.title,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = forum.title,
                            fontSize = 11.sp,
                            color = Color(0xFF333333),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 最尾部加一个“更多”按钮
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(60.dp)
                            .clickable { onMoreClick() }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more),
                            contentDescription = "更多",
                            tint = Color(0xFF999999),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "更多",
                            fontSize = 11.sp,
                            color = Color(0xFF333333)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RankListSection(model: RankListModel, onRankClick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp)) {
            Text(
                text = "今日热榜",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE65100)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            model.listModel.forEach { rank ->
                RankItem(rank, onRankClick)
            }
        }
    }
}

@Composable
fun RankItem(rank: RankModel, onRankClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onRankClick(rank.url) }
            .padding(vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(getRankColor(rank.index))
        ) {
            Text(
                text = rank.index.toString(),
                fontSize = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = rank.title,
            fontSize = 13.sp,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Text(
            text = "热度: ${rank.num}",
            fontSize = 11.sp,
            color = Color(0xFFE65100)
        )
    }
}

fun getRankColor(index: Int): Color {
    return when (index) {
        1 -> Color(0xFFFF705E)
        2 -> Color(0xFFFFB900)
        3 -> Color(0xFFA8C500)
        else -> Color(0xFFCCCCCC)
    }
}
