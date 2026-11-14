package cn.mce.starlight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.BoardFavorites
import cn.mce.starlight.data.ForumBoard

/**
 * v0.4.7：侧栏抽屉内容（从 MceApp 拆出）。
 * 目前包含：顶部账号信息、板块分组（可展开/收藏）。
 * 导航条目仍留在 MceApp（与拖动手势状态耦合）。
 */
@Composable
fun DrawerHeader(loggedIn: Boolean, username: String?) {
    Column(Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)) {
        Text("MCE星光班", style = MaterialTheme.typography.titleLarge)
        Text(
            if (loggedIn) "已登录 · ${username ?: "成员"}" else "未登录 · 打开即提示登录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
}

/**
 * 板块分组列表（二级分组标题 + 展开后的三级板块）。
 *
 * @param groups 分组名 -> 板块列表
 * @param expanded 已展开分组
 * @param selectedFid 当前正在浏览的板块 fid（用于高亮）
 * @param onToggle 展开/收起分组
 * @param onSelect 打开板块
 */
@Composable
fun DrawerBoardsSection(
    groups: List<Pair<String, List<ForumBoard>>>,
    expanded: Set<String>,
    selectedFid: Int?,
    sheen: Float,
    onToggle: (String) -> Unit,
    onSelect: (ForumBoard) -> Unit,
) {
    DrawerSection("板块")
    groups.forEach { (groupName, groupBoards) ->
        val isExpanded = groupName in expanded
        DrawerItem(
            label = (if (isExpanded) "\u25be " else "\u25b8 ") + groupName,
            selected = false,
            sheen = sheen,
            isGroupHeader = true,
        ) {
            onToggle(groupName)
        }
        if (isExpanded) {
            groupBoards.sortedByDescending { BoardFavorites.isFavorite(it.fid) }.forEach { board ->
                val starred = BoardFavorites.isFavorite(board.fid)
                DrawerItem(
                    label = (if (starred) "\u2605 " else "") + board.name,
                    selected = selectedFid == board.fid,
                    sheen = sheen,
                    secondary = true,
                    onLongClick = { BoardFavorites.toggle(board.fid, board.name) },
                ) {
                    onSelect(board)
                }
            }
        }
    }
}
