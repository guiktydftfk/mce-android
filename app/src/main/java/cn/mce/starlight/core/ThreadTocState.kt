package cn.mce.starlight.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.mce.starlight.data.Post

/**
 * v0.4.8：大屏三栏时右侧「目录」栏的数据源。
 * 帖子详情页把当前楼层列表写入这里，三栏布局的右栏直接读取；
 * 点击目录项 → [ThreadScrollRequest].pid → 详情页滚动到对应楼层。
 */
object ThreadTocState {
    var posts by mutableStateOf<List<Post>>(emptyList())
    var currentPid by mutableIntStateOf(0)
}