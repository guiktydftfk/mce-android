package cn.mce.starlight.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 特性开关：逐屏灰度替换（Web ↔ 原生）。
 * 用 mutableStateOf 以便界面实时切换（"我的"页可体验回退到 Web 列表）。
 */
object FeatureFlags {
    var threadListUsesNative by mutableStateOf(true)
    var threadDetailUsesNative by mutableStateOf(false)
}
