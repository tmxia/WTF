package top.easelink.lcg.ui.main.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


enum class MenuDisplayMode { CompactDialog, BottomSheet }


data class PopoverMenuItem(
    val icon: ImageVector? = null,
    val label: String,
    
    val active: Boolean = false,
    
    val checked: Boolean = false,
    
    val enabled: Boolean = true,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit
)

private val MenuAccent = Color(0xFFE53935)
private val MenuBg = Color(0xFFF5F5F5)
private val MenuIconTint = Color(0xFF666666)
private val MenuTextColor = Color(0xFF333333)
private val MenuGroupTitleColor = Color(0xFF888888)










@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactAnchorMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<PopoverMenuItem>>,
    title: String? = null,
    groupTitles: List<String?>? = null,
    mode: MenuDisplayMode = MenuDisplayMode.CompactDialog,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    when (mode) {
        MenuDisplayMode.CompactDialog -> {
            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth(0.74f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    if (!title.isNullOrBlank()) {
                        MenuHeader(title = title, onDismiss = onDismissRequest)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEachIndexed { groupIndex, group ->
                            group.forEach { item ->
                                PopoverMenuItemRow(
                                    item = item,
                                    itemHeight = 44.dp,
                                    verticalPadding = 12.dp
                                )
                            }
                            if (groupIndex < groups.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    thickness = 0.5.dp,
                                    color = Color(0xFFEEEEEE)
                                )
                            }
                        }
                    }
                }
            }
        }

        MenuDisplayMode.BottomSheet -> {
            SheetContent(
                title = title,
                groups = groups,
                groupTitles = groupTitles,
                onDismissRequest = onDismissRequest,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetContent(
    title: String?,
    groups: List<List<PopoverMenuItem>>,
    groupTitles: List<String?>?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxSheetHeight = with(LocalConfiguration.current) { screenHeightDp.dp * 0.55f }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {}
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (!title.isNullOrBlank()) {
                MenuHeader(title = title, onDismiss = onDismissRequest)
                Spacer(modifier = Modifier.height(8.dp))
            }
            groups.forEachIndexed { groupIndex, group ->
                val groupTitle = groupTitles?.getOrNull(groupIndex)
                if (!groupTitle.isNullOrBlank()) {
                    Text(
                        text = groupTitle,
                        fontSize = 12.sp,
                        color = MenuGroupTitleColor,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = if (groupIndex > 0) 4.dp else 0.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { item ->
                                SheetItemCell(item = item, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuHeader(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Row(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onDismiss
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PopoverMenuItemRow(
    item: PopoverMenuItem,
    itemHeight: Dp,
    verticalPadding: Dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val highlighted = item.active || item.checked
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = itemHeight)
            .alpha(if (item.enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) MenuAccent.copy(alpha = 0.1f) else MenuBg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { if (item.enabled) item.onClick() },
                onLongClick = if (item.enabled) item.onLongClick else null
            )
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (highlighted) MenuAccent else MenuIconTint
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = item.label,
            fontSize = 14.sp,
            color = if (highlighted) MenuAccent else MenuTextColor,
            fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (item.checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选",
                tint = MenuAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetItemCell(item: PopoverMenuItem, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val highlighted = item.active || item.checked
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .alpha(if (item.enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) MenuAccent.copy(alpha = 0.1f) else MenuBg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { if (item.enabled) item.onClick() },
                onLongClick = if (item.enabled) item.onLongClick else null
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (highlighted) MenuAccent else MenuIconTint
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = item.label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (highlighted) MenuAccent else MenuTextColor,
            fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (item.checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选",
                tint = MenuAccent,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
