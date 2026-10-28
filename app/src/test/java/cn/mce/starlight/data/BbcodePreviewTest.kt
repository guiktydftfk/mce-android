package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.0：发帖预览用的 BBCode 轻量渲染（独立「发帖预览」页）。
 *
 * 覆盖：加粗/斜体/下划线/颜色/链接样式、图片行（attach/img）、未知标签只去标签保留文字。
 */
class BbcodePreviewTest {

    @Test
    fun inline_styles_areParsed() {
        val segs = BbcodePreview.renderInline("[b]粗[/b][i]斜[/i][u]下划线[/u]普通")
        assertEquals("粗", segs[0].text)
        assertTrue(segs[0].bold)
        assertEquals("斜", segs[1].text)
        assertTrue(segs[1].italic)
        assertEquals("下划线", segs[2].text)
        assertTrue(segs[2].underline)
        assertEquals("普通", segs[3].text)
        assertFalse(segs[3].bold || segs[3].italic || segs[3].underline)
    }

    @Test
    fun inline_color_and_nestedBold() {
        val segs = BbcodePreview.renderInline("[b][color=#E64346]红粗[/color][/b]")
        assertEquals("红粗", segs.first().text)
        assertTrue(segs.first().bold)
        assertEquals("#E64346", segs.first().color)
    }

    @Test
    fun inline_url_withAndWithoutValue() {
        val a = BbcodePreview.renderInline("[url=https://mce.msi.cn/x]点这里[/url]")
        assertEquals("点这里", a.first().text)
        assertEquals("https://mce.msi.cn/x", a.first().url)

        val b = BbcodePreview.renderInline("[url]https://a.com[/url]")
        assertEquals("https://a.com", b.first().text)
        // [url] 无参数时按"文字即链接"处理（url 为空串，UI 侧仍会高亮）
        assertTrue(b.first().url != null)
    }

    @Test
    fun unknownTags_areStrippedButTextKept() {
        val segs = BbcodePreview.renderInline("[quote]引用内容[/quote][hide]隐藏内容[/hide]普通")
        val joined = segs.joinToString("") { it.text }
        assertEquals("引用内容隐藏内容普通", joined)
        assertFalse(joined.contains("["))
    }

    @Test
    fun block_attachLine_becomesImageWithAid() {
        val blocks = BbcodePreview.render("第一段\n[attach]12345[/attach]\n第二段")
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is BbcodePreview.Block.Text)
        val img = blocks[1] as BbcodePreview.Block.Image
        assertEquals("12345", img.attachAid)
        assertTrue(blocks[2] is BbcodePreview.Block.Text)
    }

    @Test
    fun block_imgLine_becomesImageWithUrl() {
        val blocks = BbcodePreview.render("[img]https://mce.msi.cn/a.jpg[/img]")
        assertEquals(1, blocks.size)
        val img = blocks[0] as BbcodePreview.Block.Image
        assertEquals("https://mce.msi.cn/a.jpg", img.src)
    }

    @Test
    fun block_alignCenter_appliesToTextBlock() {
        val blocks = BbcodePreview.render("[align=center]居中文字[/align]")
        assertEquals(1, blocks.size)
        val text = blocks[0] as BbcodePreview.Block.Text
        assertEquals("center", text.align)
        assertEquals("居中文字", text.segments.joinToString("") { it.text })
    }

    @Test
    fun block_alignRight_coversMultipleLines() {
        val blocks = BbcodePreview.render("[align=right]第一行x\n第二行[/align]")
        val texts = blocks.filterIsInstance<BbcodePreview.Block.Text>()
        assertEquals(2, texts.size)
        assertTrue(texts.all { it.align == "right" })
    }

    @Test
    fun block_media_becomesVideo() {
        val blocks = BbcodePreview.render("[media=x,500,375]https://www.bilibili.com/video/BV1xx[/media]")
        assertEquals(1, blocks.size)
        val video = blocks[0] as BbcodePreview.Block.Video
        assertEquals("https://www.bilibili.com/video/BV1xx", video.src)
    }

    @Test
    fun block_emptyText_returnsNoBlocks() {
        assertTrue(BbcodePreview.render("   \n\n  ").isEmpty())
    }
}
