package top.easelink.lcg.ui.main.discover.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import top.easelink.lcg.ui.main.discover.model.DiscoverModel
import top.easelink.lcg.ui.main.discover.model.ForumListModel
import top.easelink.lcg.ui.main.discover.model.RankListModel

@Composable
fun DiscoverScreenCompose(
    models: List<DiscoverModel>,
    onForumClick: (String, String) -> Unit,
    onRankClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(models) { model ->
            when (model) {
                is ForumListModel -> ForumNavigationSection(model, onForumClick)
                is RankListModel -> RankListSection(model, onRankClick)
            }
        }
    }
}

@Composable
fun ForumNavigationSection(model: ForumListModel, onForumClick: (String, String) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "常用板块导航",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "右滑查看更多 >",
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 简易网格展示板块（采用 FlowRow 或手动 Row）
            // 这里用 Row 包裹，支持自动换行
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                model.forumList.take(4).forEach { forum ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(60.dp)
                            .clickable { onForumClick(forum.title, forum.url) }
                    ) {
                        Icon(
                            painter = painterResource(id = forum.drawableRes),
                            contentDescription = forum.title,
                            tint = Color.Unspecified, // 保留原图标颜色
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "今日热榜",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100) // 橙红色
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            model.listModel.forEach { rank ->
                RankItem(rank, onRankClick)
            }
        }
    }
}

@Composable
fun RankItem(rank: top.easelink.lcg.ui.main.discover.model.RankModel, onRankClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRankClick(rank.url) }
            .padding(vertical = 8.dp)
    ) {
        // 排名徽章
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
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // 标题
        Text(
            text = rank.title,
            fontSize = 13.sp,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // 热度
        Text(
            text = "热度: ${rank.num}",
            fontSize = 11.sp,
            color = Color(0xFFE65100)
        )
    }
}

fun getRankColor(index: Int): Color {
    return when (index) {
        1 -> Color(0xFFFF705E) // 红
        2 -> Color(0xFFFFB900) // 橙
        3 -> Color(0xFFA8C500) // 绿
        else -> Color(0xFFCCCCCC) // 灰
    }
}
