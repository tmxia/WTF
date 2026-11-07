package top.easelink.lcg.ui.main.discover.view

import android.content.Context
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import timber.log.Timber
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.discover.model.DiscoverModel
import top.easelink.lcg.ui.main.discover.model.ForumListModel
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
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
    onRefresh: () -> Unit,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    val forumModel = models.filterIsInstance<ForumListModel>().firstOrNull()
    val rankModels = models.filterIsInstance<RankListModel>().flatMap { it.listModel }
    val allForums = forumModel?.forumList ?: emptyList()

    if (rankModels.isNotEmpty()) {
        Timber.d("【发现页-UI】界面刷新，第一条热榜: ${rankModels.first().title} (热度: ${rankModels.first().num})")
    }

    // 自定义板块状态管理
    val prefs = remember { context.getSharedPreferences("forum_customize", Context.MODE_PRIVATE) }
    var displayUrls by remember { mutableStateOf(loadSavedUrls(prefs, allForums)) }
    var showCustomizeDialog by remember { mutableStateOf(false) }

    // 当 allForums 发生变化时（如刷新），重新同步展示列表
    LaunchedEffect(allForums) {
        displayUrls = loadSavedUrls(prefs, allForums)
    }

    val displayedForums = displayUrls.mapNotNull { url -> allForums.find { it.url == url } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(top = 12.dp)
    ) {
        if (forumModel != null && displayedForums.isNotEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                ForumNavigationSection(
                    forums = displayedForums,
                    onForumClick = onForumClick,
                    onMoreClick = onMoreForumsClick,
                    onLongClick = { showCustomizeDialog = true } // 触发长按定制
                )
            }
        }

        if (models.isEmpty() && !isLoading) {
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
                            enabled = !isLoading,
                            modifier = Modifier.size(24.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFFE65100),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "刷新",
                                    tint = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                }
                items(rankModels, key = { it.url }) { rank ->
                    RankItemCard(rank, onRankClick, onRankLongClick)
                }
            }
        }
    }

    // 定制弹窗
    if (showCustomizeDialog) {
        ForumCustomizeDialog(
            allForums = allForums,
            currentUrls = displayUrls,
            onDismiss = { showCustomizeDialog = false },
            onSave = { newUrls ->
                displayUrls = newUrls
                prefs.edit().putString("forum_urls", newUrls.joinToString(",")).apply()
                showCustomizeDialog = false
            }
        )
    }
}

// 读取已保存的配置，如果没有则默认取前8个
private fun loadSavedUrls(prefs: android.content.SharedPreferences, allForums: List<ForumNavigationModel>): List<String> {
    val saved = prefs.getString("forum_urls", null)
    if (saved != null) {
        return saved.split(",").filter { it.isNotBlank() }
    }
    return allForums.take(8).map { it.url }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ForumNavigationSection(
    forums: List<ForumNavigationModel>,
    onForumClick: (String, String) -> Unit,
    onMoreClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { }, // 点击卡片本身不做事，点击里面的具体项才跳转
                onLongClick = onLongClick
            )
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
                items(forums, key = { it.url }) { forum ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onForumClick(forum.title, forum.url) }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(painterResource(id = forum.drawableRes), forum.title, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(forum.title, fontSize = 11.sp, fontWeight = FontWeight.Normal, color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMoreClick() }
                            .padding(vertical = 4.dp)
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

@Composable
fun ForumCustomizeDialog(
    allForums: List<ForumNavigationModel>,
    currentUrls: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val selectedUrls = remember { mutableStateListOf<String>().apply { addAll(currentUrls) } }
    // 记录主版块的折叠状态
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义常用板块", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("已选 (长按上下图标排序)", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(selectedUrls) { url ->
                        val forum = allForums.find { it.url == url }
                        if (forum != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Checkbox(checked = true, onCheckedChange = { selectedUrls.remove(url) })
                                Text(forum.title, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(
                                    onClick = {
                                        val index = selectedUrls.indexOf(url)
                                        if (index > 0) {
                                            val tmp = selectedUrls[index]; selectedUrls[index] = selectedUrls[index - 1]; selectedUrls[index - 1] = tmp
                                        }
                                    }, modifier = Modifier.size(32.dp)
                                ) { Icon(Icons.Default.KeyboardArrowUp, "上移", modifier = Modifier.size(20.dp), tint = Color.Gray) }
                                IconButton(
                                    onClick = {
                                        val index = selectedUrls.indexOf(url)
                                        if (index != -1 && index < selectedUrls.size - 1) {
                                            val tmp = selectedUrls[index]; selectedUrls[index] = selectedUrls[index + 1]; selectedUrls[index + 1] = tmp
                                        }
                                    }, modifier = Modifier.size(32.dp)
                                ) { Icon(Icons.Default.KeyboardArrowDown, "下移", modifier = Modifier.size(20.dp), tint = Color.Gray) }
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("未选 (点击主版块折叠/展开)", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                
                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                    val unselected = allForums.filter { it.url !in selectedUrls }
                    // 分离主版块和子版块
                    val mainForums = unselected.filter { !it.isSubForum }
                    val subForums = unselected.filter { it.isSubForum }

                    items(mainForums) { main ->
                        // 找到属于该主版块的子版块
                        val children = subForums.filter { it.parentTitle == main.title }
                        val isExpanded = expandedGroups[main.title] == true

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                    // 点击整行切换展开状态
                                    expandedGroups[main.title] = !isExpanded 
                                }
                            ) {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = { 
                                        selectedUrls.add(main.url)
                                        // 同时勾选子版块（可选逻辑，这里保持简单：只选主版块）
                                    }
                                )
                                Text(main.title, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (children.isNotEmpty()) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            // 渲染子版块
                            if (isExpanded && children.isNotEmpty()) {
                                children.forEach { child ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 2.dp, bottom = 2.dp)
                                    ) {
                                        Checkbox(checked = false, onCheckedChange = { selectedUrls.add(child.url) })
                                        Text(child.title, modifier = Modifier.weight(1f), fontSize = 13.sp, color = Color.DarkGray)
                                    }
                                }
                            }
                        }
                    }
                    
                    // 如果有些子版块没归属主版块，单独展示
                    val orphanSubs = subForums.filter { it.parentTitle == null || mainForums.none { m -> m.title == it.parentTitle } }
                    items(orphanSubs) { child ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Checkbox(checked = false, onCheckedChange = { selectedUrls.add(child.url) })
                            Text(child.title, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        }
                    }
                    
                    // 如果未选列表彻底为空，给个文字提示
                    if (unselected.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("已加载全部可用板块", fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selectedUrls.toList()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
