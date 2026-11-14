package cn.mce.starlight.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v0.4.7：统一顶部安全区收敛。
 * 异形屏/状态栏异常时 inset 可能非常大（历史上出现过整页大空白），
 * 统一在 96dp 处封顶；各页不再各自复制 coerceAtMost(96.dp)。
 */
fun clampTopInset(value: Dp, max: Dp = 96.dp): Dp = value.coerceAtMost(max)
