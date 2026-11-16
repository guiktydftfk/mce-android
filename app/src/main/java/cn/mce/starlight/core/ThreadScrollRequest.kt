package cn.mce.starlight.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * v0.4.8：大屏三栏时右侧目录点击 → 请求左侧帖子列表滚动到指定楼层。
 * pid = 0 表示无请求。
 */
object ThreadScrollRequest {
    var pid by mutableIntStateOf(0)
}