package top.easelink.lcg.ui.main.discover.view

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import timber.log.Timber
import top.easelink.lcg.R
import top.easelink.lcg.ui.main.discover.model.DiscoverModel
import top.easelink.lcg.ui.main.discover.model.ForumListModel
import top.easelink.lcg.ui.main.discover.model.ForumNavigationModel
import top.easelink.lcg.ui.main.discover.model.RankListModel
import top.easelink.lcg.ui.main.discover.model.RankModel
import top.easelink.lcg.ui.main.discover.model.generateAllForums

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

    val prefs = remember { context.getSharedPreferences("forum_customize", Context.MODE_PRIVATE) }
    var displayUrls by remember { mutableStateOf(loadSavedUrls(prefs, allForums)) }
    var showCustomizeDialog by remember { mutableStateOf(false) }

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
                    onLongClick = { showCustomizeDialog = true }
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

    if (showCustomizeDialog) {
        ForumCustomizeDialog(
            allForums = allForums,
            currentUrls = displayUrls,
            onDismiss = { showCustomizeDialog = false },
            onReset = {
                val hardcodedForums = generateAllForums(context)
                val resetUrls = hardcodedForums.mapNotNull { original ->
                    val fidMatch = Regex("""(?:forum-|fid=)([0-9]+)""").find(original.url)
                    val fid = fidMatch?.groupValues?.get(1)
                    if (fid != null) {
                        val matchedUrl = allForums.find { 
                            it.url.contains("forum-$fid-1.html") || 
                            it.url.contains("fid=$fid") || 
                            it.url.contains("forum-$fid-") 
                        }?.url 
                        matchedUrl ?: "forum-$fid-1.html"
                    } else {
                        original.url
                    }
                }.distinct()

                displayUrls = resetUrls
                prefs.edit().putString("forum_urls", resetUrls.joinToString(",")).apply()
                showCustomizeDialog = false
            },
            onSave = { newUrls ->
                displayUrls = newUrls
                prefs.edit().putString("forum_urls", newUrls.joinToString(",")).apply()
                showCustomizeDialog = false
            }
        )
    }
}

private fun loadSavedUrls(prefs: android.content.SharedPreferences, allForums: List<ForumNavigationModel>): List<String> {
    val saved = prefs.getString("forum_urls", null)
    if (saved != null) {
        val validUrls = allForums.map { it.url }.toSet()
        val cleaned = saved.split(",").filter { it.isNotBlank() && it in validUrls }
        if (cleaned.isNotEmpty()) {
            return cleaned
        }
    }
    return allForums.filter { !it.isSubForum }.take(8).map { it.url }
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
                onClick = { },
                onLongClick = onLongClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("常用板块导航", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                Text("右滑查看更多 >", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Color(0xFF999999), modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onMoreClick() })
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(forums, key = { it.url }) { forum ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onForumClick(forum.title, forum.url) }
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
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onMoreClick() }
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
    onReset: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val selectedUrls = remember { mutableStateListOf<String>().apply { addAll(currentUrls) } }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF5F5F5),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.wrapContentHeight()) {
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自定义常用板块", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        text = "恢复默认",
                        fontSize = 13.sp,
                        color = Color(0xFFE53935),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onReset() }
                    )
                }

                
                Column(
                    modifier = Modifier
                        .wrapContentHeight()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("已选板块", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                    
                    
                    LazyColumn(modifier = Modifier.heightIn(max = 110.dp).fillMaxWidth()) {
                        items(selectedUrls) { url ->
                            val forum = allForums.find { it.url == url }
                            if (forum != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(forum.title, modifier = Modifier.weight(1f), fontSize = 13.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    
                                    IconButton(
                                        onClick = {
                                            val index = selectedUrls.indexOf(url)
                                            if (index > 0) {
                                                val tmp = selectedUrls[index]; selectedUrls[index] = selectedUrls[index - 1]; selectedUrls[index - 1] = tmp
                                            }
                                        }, modifier = Modifier.size(24.dp)
                                    ) { Icon(Icons.Default.KeyboardArrowUp, "上移", modifier = Modifier.size(16.dp), tint = Color.Gray) }
                                    
                                    IconButton(
                                        onClick = {
                                            val index = selectedUrls.indexOf(url)
                                            if (index != -1 && index < selectedUrls.size - 1) {
                                                val tmp = selectedUrls[index]; selectedUrls[index] = selectedUrls[index + 1]; selectedUrls[index + 1] = tmp
                                            }
                                        }, modifier = Modifier.size(24.dp)
                                    ) { Icon(Icons.Default.KeyboardArrowDown, "下移", modifier = Modifier.size(16.dp), tint = Color.Gray) }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "移除",
                                        fontSize = 13.sp,
                                        color = Color.Red,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { selectedUrls.remove(url) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)
                    Text("点击添加板块", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                    
                    
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp).fillMaxWidth()) {
                        val unselected = allForums.filter { it.url !in selectedUrls }
                        val mainForums = unselected.filter { !it.isSubForum }
                        val subForums = unselected.filter { it.isSubForum }

                        items(mainForums) { main ->
                            val children = subForums.filter { it.parentTitle == main.title }
                            val isExpanded = expandedGroups[main.title] == true

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { 
                                            if (children.isNotEmpty()) {
                                                expandedGroups[main.title] = !isExpanded
                                            } else {
                                                selectedUrls.add(main.url)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "+ ${main.title}",
                                        fontSize = 13.sp,
                                        color = Color.DarkGray,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (children.isNotEmpty()) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "折叠",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                
                                if (isExpanded) {
                                    children.forEach { child ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFEEEEEE))
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { selectedUrls.add(child.url) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "+ ${child.title}",
                                                fontSize = 12.sp,
                                                color = Color.Black,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        
                        
                        val orphanSubs = subForums.filter { sub -> mainForums.none { it.title == sub.parentTitle } }
                        items(orphanSubs) { child ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { selectedUrls.add(child.url) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = "+ ${child.title}", fontSize = 13.sp, color = Color.DarkGray)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                    ) {
                        Text("取消", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(selectedUrls.toList()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp) 
                    ) {
                        Text("保存", color = Color.White, fontSize = 14.sp)
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
                onLongClick = { onRankLongClick(rank.url) },
                interactionSource = remember { MutableInteractionSource() },
                indication = null
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
