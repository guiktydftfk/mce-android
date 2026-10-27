package cn.mce.starlight.data

/** 帖子正文内容块：保证文字与图片按原始顺序交错渲染 */
sealed interface ContentBlock {
    data class Text(
        val text: String,
        /** 文字样式片段（v0.3.8：彩色文字/加粗/下划线等）；默认空 = 与旧行为完全一致 */
        val spans: List<TextSpan> = emptyList(),
        /** 段落类型：普通段落 / 引用块 */
        val kind: TextKind = TextKind.Paragraph,
    ) : ContentBlock

    data class Image(val url: String) : ContentBlock
}

/** 正文段落类型（v0.3.8） */
enum class TextKind { Paragraph, Quote }
