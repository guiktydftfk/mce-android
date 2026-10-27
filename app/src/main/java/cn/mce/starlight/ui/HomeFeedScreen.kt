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
import cn.mce.starlight.core.SettingsStore
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
            // 过滤阈值变化时也重建列表（resetKey 变化 → 重新加载第 1 页）
            resetKey = Triple(view, reloadKey, SettingsStore.feedMinChars),
            topPadding = topInset + 104.dp,
            horizontalPadding = 12.dp,
            emptyText = if (SettingsStore.feedFilterEnabled) {
                "当前设置过滤了少于 ${SettingsStore.feedMinChars} 字的帖子\n可在「设置 → 内容」里调整或关闭"
            } else {
                "这个分类还没有帖子，下拉刷新试试"
            },
            loader = { page ->
                val base = repo.loadGuide(view, page)
                // v0.3.9：论坛「导读」偶尔不收录自己刚发的帖子（版块列表能看到、首页看不到，
                // 但仍会收到该帖的新消息提醒）。首页第一页额外合并「我的最近主题」，
                // 把自己缺的新帖补到最前面，保证「我刚发的帖子」在首页一定可见。
                if (page == 1 && view == "newthread") {
                    val mine = runCatching { repo.loadGuide("my", 1) }.getOrNull()
                    val extra = mine?.threads
                        ?.filter { t -> base.threads.none { it.id == t.id } }
                        ?.take(2)
                        .orEmpty()
                    if (extra.isEmpty()) base else base.copy(threads = extra + base.threads)
                } else {
                    base
                }
            },
            // v0.3.8：按字数过滤（只看首页信息流，不影响板块/搜索）
            filter = { thread ->
                val min = SettingsStore.feedMinChars
                min <= 0 || threadCharCount(thread) >= min
            },
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

/** 首页过滤用的「字数」：优先用摘要（正文节选），没有摘要时退回标题长度 */
private fun threadCharCount(thread: ThreadSummary): Int {
    val s = thread.summary?.trim().orEmpty()
    return if (s.isNotEmpty()) s.length else thread.title.trim().length
}

