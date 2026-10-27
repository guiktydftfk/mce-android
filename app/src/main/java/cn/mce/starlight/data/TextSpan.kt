package cn.mce.starlight.data

/**
 * 正文文字样式片段（v0.3.8）。
 *
 * 论坛正文里的 `<font color>`、`<strong>`、`<u>`、`<font size>` 等样式会被解析成
 * 「半开区间 [start, end) + 纯 Int ARGB 颜色」的片段，UI 层再用 `AnnotatedString` 还原。
 * 放在 data 层且不依赖 Compose/android.text，方便纯 JVM 单测。
 */
data class TextSpan(
    val start: Int,
    val end: Int,
    /** ARGB；null = 沿用主题文字色 */
    val color: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    /** 相对字号（em），来自 `<font size="1..7">`；null = 默认 */
    val sizeEm: Float? = null,
)
