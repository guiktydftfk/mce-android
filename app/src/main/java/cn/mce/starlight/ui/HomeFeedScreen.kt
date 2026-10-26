package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.ThreadSummary

/** 导读分类（对应 forum.php?mod=guide&view=xxx） */
private val GUIDE_TABS = listOf(
    "newthread" to "最新",
    "hot" to "热帖",
    "digest" to "精华",
    "sofa" to "沙发",
)

/**
 * 首页动态流（借鉴鸿蒙版设计）：
 * 卡片式布局 —— 头像/作者 + 版块标签 + 时间、标题、摘要、大图、分类与回复数。
 *
 * v0.3.3 批 5：列表状态/刷新/加载更多/空态统一由 [PagedThreadList] 承担；
 * 换导读分类时列表与滚动位置一起重置；去掉重复的顶部 LinearProgressIndicator。
 */
@Composable
fun HomeFeedScreen(
    onMenu: () -> Unit,
    reloadKey: Int = 0,
    onOpenThread: (ThreadSummary) -> Unit,
    onCompose: () -> Unit,
) {
    val repo = remember { HttpForumRepository() }
    var view by remember { mutableStateOf("newthread") }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        PagedThreadList(
            resetKey = view to reloadKey,
            topPadding = topInset + 104.dp,
            horizontalPadding = 12.dp,
            emptyText = "这个分类还没有帖子，下拉刷新试试",
            loader = { page -> repo.loadGuide(view, page) },
            onOpenThread = onOpenThread,
        )

        ImmersiveTopBar(
            title = "全部最新",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
            actionLabel = "发帖",
            onAction = onCompose,
        )

        // 导读分类
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset + 52.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GUIDE_TABS.forEach { (value, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (view == value) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (view == value) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { if (view != value) view = value }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
