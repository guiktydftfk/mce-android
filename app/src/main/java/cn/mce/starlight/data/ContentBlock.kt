package cn.mce.starlight.data

/** 帖子正文内容块：保证文字与图片按原始顺序交错渲染 */
sealed interface ContentBlock {
    data class Text(
        val text: String,
        /** 文字样式片段（v0.3.8：彩色文字/加粗/下划线等）；默认空 = 与旧行为完全一致 */
        val spans: List<TextSpan> = emptyList(),
        /** 段落类型：普通段落 / 引用块 */
        val kind: TextKind = TextKind.Paragraph,
        /** v0.3.10：正文里的超链接（点击用系统默认浏览器打开） */
        val links: List<TextLink> = emptyList(),
    ) : ContentBlock

    data class Image(val url: String) : ContentBlock

    /**
     * v0.4.0：帖子里的 HTML 表格（BBCode `[table]` 或编辑器粘贴的表格）。
     * 旧实现把 `<tr>/<td>` 摊平成一行行文字，长表格完全没法读；现在单独成块渲染。
     */
    data class Table(
        val rows: List<List<String>>,
        val hasHeader: Boolean = true,
    ) : ContentBlock
}

/** 正文超链接区间：[start, end) 是链接文字，[url] 是跳转地址（绝对地址） */
data class TextLink(val start: Int, val end: Int, val url: String)

/** 正文段落类型（v0.3.8） */
enum class TextKind { Paragraph, Quote }
