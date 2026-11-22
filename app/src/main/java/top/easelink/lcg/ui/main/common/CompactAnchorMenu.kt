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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 锚点紧凑弹出菜单的单一项。 */
data class PopoverMenuItem(
    val icon: ImageVector? = null,
    val label: String,
    /** 开关类激活态：文字与图标以主色高亮。 */
    val active: Boolean = false,
    /** 单选类：右侧显示对勾。 */
    val checked: Boolean = false,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit
)

private val MenuAccent = Color(0xFFE53935)
private val MenuBg = Color(0xFFF5F5F5)
private val MenuIconTint = Color(0xFF666666)
private val MenuTextColor = Color(0xFF333333)

/**
 * 统一风格的居中选择对话框：与搜索页排序/板块弹窗保持同一套视觉规范——
 * 白色 20dp 圆角卡片、半透明遮罩、标题 + 关闭按钮；选项为 12dp 圆角浅灰长条，
 * 选中/激活项以主色 10% 浅红底 + 主色文字 + 右侧对勾呈现。
 *
 * @param title 对话框标题；为 null 时不渲染头部标题栏。
 * @param groups 分组列表，每组若干菜单项；组间自动插入分隔线。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactAnchorMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<PopoverMenuItem>>,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            if (!title.isNullOrBlank()) {
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
                                onClick = onDismissRequest
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
                Spacer(modifier = Modifier.height(16.dp))
            }

            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = Color(0xFFEEEEEE)
                    )
                }
                group.forEach { item ->
                    PopoverMenuItemRow(item)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PopoverMenuItemRow(item: PopoverMenuItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val highlighted = item.active || item.checked
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) MenuAccent.copy(alpha = 0.1f) else MenuBg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = item.onClick,
                onLongClick = item.onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
