package top.easelink.lcg.ui.main.discover.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreenCompose(
    models: List<DiscoverModel>,
    onForumClick: (String, String) -> Unit,
    onMoreForumsClick: () -> Unit,
    onRankClick: (String) -> Unit,
    onRankLongClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val forumModel = models.filterIsInstance<ForumListModel>().firstOrNull()
    val rankModels = models.filterIsInstance<RankListModel>().flatMap { it.listModel }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(top = 12.dp)
    ) {
        if (forumModel != null) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                ForumNavigationSection(forumModel, onForumClick, onMoreForumsClick)
            }
        }

        if (models.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无数据，请点击刷新", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日热榜",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE65100)
                        )
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = Color(0xFFE65100)
                            )
                        }
                    }
                }
                items(rankModels) { rank ->
                    RankItemCard(rank, onRankClick, onRankLongClick)
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
                Text("常用板块导航", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                Text("右滑查看更多 >", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Color(0xFF999999), modifier = Modifier.clickable { onMoreClick() })
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(model.forumList.take(8)) { forum ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(56.dp).clickable { onForumClick(forum.title, forum.url) }
                    ) {
                        Icon(painterResource(id = forum.drawableRes), forum.title, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(forum.title, fontSize = 11.sp, fontWeight = FontWeight.Normal, color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(56.dp).clickable { onMoreClick() }
                    ) {
                        Icon(painterResource(id = R.drawable.ic_more), "更多", tint = Color(0xFF999999), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("更多", fontSize = 11.sp, fontWeight = FontWeight.Normal, color = Color(0xFF333333))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RankItemCard(
    rank: RankModel,
    onRankClick: (String) -> Unit,
    onRankLongClick: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onRankClick(rank.url) },
                onLongClick = { onRankLongClick(rank.url) }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp).clip(CircleShape).background(getRankColor(rank.index))
            ) {
                Text(rank.index.toString(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = rank.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "热度: ${rank.num}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFE65100)
            )
        }
    }
}

fun getRankColor(index: Int): Color {
    return when (index) {
        1 -> Color(0xFFFF5252)
        2 -> Color(0xFFFFB300)
        3 -> Color(0xFF8BC34A)
        else -> Color(0xFFCCCCCC)
    }
}
