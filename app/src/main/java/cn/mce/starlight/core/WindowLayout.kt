package cn.mce.starlight.core

/**
 * v0.4.7：窗口形态判定（纯函数，便于单测）。
 * 平板/横屏双栏规则：宽度 ≥600dp，且（横屏 或 宽度 ≥720dp）。
 * 这样手机竖屏始终单栏，折叠屏展开/平板竖屏也能获得双栏。
 */
object WindowLayout {
    fun isDualPane(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= 600 && (widthDp > heightDp || widthDp >= 720)
}
