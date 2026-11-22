package top.easelink.lcg.ui.main.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.DropdownMenu
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
private val MenuIconTint = Color(0xFF666666)
private val MenuTextColor = Color(0xFF333333)

/**
 * 统一风格的锚点紧凑弹出菜单：绑定右上角触发按钮就近弹出窄面板，
 * 分组之间以细分隔线隔开；单选项右侧带对勾，开关项以主色高亮。
 *
 * @param groups 分组列表，每组若干菜单项；组间自动插入分隔线。
 */
@Composable
fun CompactAnchorMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<PopoverMenuItem>>,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .width(184.dp)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(vertical = 4.dp)
        ) {
            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = item.onClick,
                onLongClick = item.onLongClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (item.active || item.checked) MenuAccent else MenuIconTint
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = item.label,
            fontSize = 14.sp,
            color = if (item.active || item.checked) MenuAccent else MenuTextColor,
            fontWeight = if (item.active || item.checked) FontWeight.Medium else FontWeight.Normal,
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
