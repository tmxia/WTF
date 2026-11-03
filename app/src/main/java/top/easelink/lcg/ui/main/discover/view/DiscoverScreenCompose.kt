@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package top.easelink.lcg.ui.main.discover.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onForumClick: (String, String) -> Unit,
    onMoreForumsClick: () -> Unit,
    onRankClick: (String) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(models) { model ->
                when (model) {
                    is ForumListModel -> ForumNavigationSection(model, onForumClick, onMoreForumsClick)
                    is RankListModel -> RankListSection(model, onRankClick)
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
                    fontSize = 11.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.clickable { onMoreClick() }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 只展示前 4 个板块
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
                
                // 添加“更多”按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(60.dp)
                        .clickable { onMoreClick() }
                ) {
                    Icon(
                        painter = painterResource(id = top.easelink.lcg.R.drawable.ic_more),
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
fun RankItem(rank: top.easelink.lcg.ui.main.discover.model.RankModel, onRankClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
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
        
        Spacer(modifier = Modifier.width(10.dp))
        
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
