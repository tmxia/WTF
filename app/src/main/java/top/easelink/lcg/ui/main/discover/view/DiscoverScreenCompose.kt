@file:OptIn(ExperimentalLayoutApi::class)

package top.easelink.lcg.ui.main.discover.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

    // 统一外边距与间距，与帖子列表页保持绝对一致
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        models.forEach { model ->
            when (model) {
                is ForumListModel -> {
                    item {
                        ForumNavigationSection(model, onForumClick, onMoreForumsClick)
                    }
                }
                is RankListModel -> {
                    // 把热榜标题当作列表里的一个纯文本项
                    item {
                        Text(
                            text = "今日热榜",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                        )
                    }
                    // 核心改变：把热榜里的每一个 Item 拆成独立的卡片，使用统一的间距
                    items(model.listModel) { rank ->
                        RankItemCard(rank, onRankClick)
                    }
                }
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
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.clickable { onMoreClick() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(model.forumList.take(8)) { forum ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(56.dp)
                            .clickable { onForumClick(forum.title, forum.url) }
                    ) {
                        Icon(
                            painter = painterResource(id = forum.drawableRes),
                            contentDescription = forum.title,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = forum.title,
                            fontSize = 11.sp,
                            color = Color(0xFF333333),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(56.dp)
                            .clickable { onMoreClick() }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more),
                            contentDescription = "更多",
                            tint = Color(0xFF999999),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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

// 核心重构：将热榜每一项做成独立的帖子级卡片
@Composable
fun RankItemCard(rank: RankModel, onRankClick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRankClick(rank.url) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp) // 和 ArticleCard 完全一样的 16dp 内边距
        ) {
            // 优化后的排名色块
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp) // 稍微放大一点，更饱满
                    .clip(CircleShape)
                    .background(getRankColor(rank.index))
            ) {
                Text(
                    text = rank.index.toString(),
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = rank.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium, // 标题加粗，对齐帖子卡片
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "热度: ${rank.num}",
                fontSize = 13.sp,
                color = Color(0xFFE65100)
            )
        }
    }
}

fun getRankColor(index: Int): Color {
    return when (index) {
        1 -> Color(0xFFFF5252) // 红色
        2 -> Color(0xFFFFB300) // 金色
        3 -> Color(0x8BC34A)   // 绿色
        else -> Color(0xFFCCCCCC) // 灰色
    }
}
