package cn.mce.starlight.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 编辑框「光标处插入」小工具（v0.3.6 从 NewThreadScreen 抽出，发帖页与回帖框共用）。
 *
 * 注意：统一用 TextFieldValue 全量重建而非拼接 String，否则插入点会跑到文本末尾；
 * 插入后 composition 置空（提交当前输入法组字），避免中文输入法残留组字区导致插入错位。
 */

/** 在光标/选区处插入 [insert]，光标落在插入文本末尾 */
fun TextFieldValue.insertAtCursor(insert: String): TextFieldValue {
    if (insert.isEmpty()) return this
    val start = selection.min
    val end = selection.max
    return TextFieldValue(
        text = text.replaceRange(start, end, insert),
        selection = TextRange(start + insert.length),
        composition = null,
    )
}

/**
 * 有选区则用 [open]/[close] 包裹选区；无选区插入占位文本，光标放到标签中间。
 *
 * v0.3.10：包裹后**保持选中被包裹的文字**（而不是把光标丢到标签末尾），
 * 这样「选中 → 加粗 → 再点颜色/斜体/下划线」可以连续叠加，符合网页编辑器的使用习惯。
 */
fun TextFieldValue.wrapSelection(
    open: String,
    close: String,
    placeholder: String,
): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val hasSelection = end > start
    val selected = text.substring(start, end).ifBlank { placeholder }
    val newText = text.replaceRange(start, end, open + selected + close)
    val innerStart = start + open.length
    val selectionRange = if (hasSelection) {
        TextRange(innerStart, innerStart + selected.length)
    } else {
        TextRange(innerStart + selected.length)
    }
    return TextFieldValue(
        text = newText,
        selection = selectionRange,
        composition = null,
    )
}

/**
 * 生成 Discuz @ 提及的 BBCode。
 *
 * - 有 uid 时用 `[url=...]@昵称[/url]`：即使服务端没识别成 @ 提醒，也至少渲染成可点用户名；
 * - uid 缺失时退化为纯文本 `@昵称 `（留一个空格，Discuz 的 @ 正则要求后接分隔符）。
 */
fun mentionBbcode(uid: Int?, name: String): String {
    val clean = name.trim()
    if (clean.isEmpty()) return ""
    return if (uid != null && uid > 0) {
        "[url=home.php?mod=space&uid=$uid]@$clean[/url] "
    } else {
        "@$clean "
    }
}
