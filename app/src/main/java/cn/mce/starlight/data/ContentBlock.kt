package cn.mce.starlight.data

/** 帖子正文内容块：保证文字与图片按原始顺序交错渲染 */
sealed interface ContentBlock {
    data class Text(val text: String) : ContentBlock
    data class Image(val url: String) : ContentBlock
}
