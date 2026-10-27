package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 正文样式解析（v0.3.8「无法显示彩色文字」修复的回归）。
 *
 * 断言方式刻意用 `text.substring(span.start, span.end)` 而不是硬编码偏移：
 * 一次同时验证「HTML 规范化后的偏移重映射」与「颜色落在正确的文字上」。
 */
class ColorSpanParseTest {

    private fun load(name: String): String {
        val candidates = listOf(
            File("src/test/resources/$name"),
            File("app/src/test/resources/$name"),
        )
        val file = candidates.firstOrNull { it.exists() } ?: error("fixture not found: $name")
        return file.readText(Charsets.UTF_8)
    }

    private fun parseFixture(): ThreadDetail {
        val html = load("color_spans.html")
        return ThreadDetailParser.parse(html, "https://mce.msi.cn/forum.php?mod=viewthread&tid=1001", 1001, 1)
    }

    private fun ThreadDetail.textBlocks(): List<ContentBlock.Text> =
        posts.flatMap { it.blocks }.filterIsInstance<ContentBlock.Text>()

    private fun ContentBlock.Text.spansCovering(needle: String): List<TextSpan> {
        val idx = text.indexOf(needle)
        if (idx < 0) return emptyList()
        return spans.filter { it.start <= idx && it.end >= idx + needle.length }
    }

    @Test
    fun `hex color is parsed and covers the right text`() {
        val block = parseFixture().textBlocks().first { it.text.contains("红色文字") }
        val span = block.spansCovering("红色文字").firstOrNull()
        assertNotNull("红色文字应有颜色样式", span)
        assertEquals(0xFFFF0000.toInt(), span!!.color)
        assertEquals("红色文字", block.text.substring(span.start, span.end))
    }

    @Test
    fun `short hex color is expanded`() {
        val block = parseFixture().textBlocks().first { it.text.contains("短十六进制") }
        val span = block.spansCovering("短十六进制").firstOrNull()
        assertNotNull(span)
        assertEquals(0xFFFF0000.toInt(), span!!.color)
    }

    @Test
    fun `named color and bold are parsed`() {
        val blocks = parseFixture().textBlocks()
        val namedBlock = blocks.first { it.text.contains("命名色") }
        assertEquals(0xFFFF00FF.toInt(), namedBlock.spansCovering("命名色").first().color)

        val boldBlock = blocks.first { it.text.contains("加粗文字") }
        assertTrue("strong 应解析为加粗", boldBlock.spansCovering("加粗文字").first().bold)
    }

    @Test
    fun `inherit color falls back to theme color (no color span)`() {
        val block = parseFixture().textBlocks().first { it.text.contains("继承色") }
        // color="inherit" 不产生颜色；该段可能没有任何 span
        assertTrue(
            "inherit 不应解析出具体颜色",
            block.spansCovering("继承色").all { it.color == null },
        )
    }

    @Test
    fun `font size underline strike and span style are parsed`() {
        val blocks = parseFixture().textBlocks()
        val sizeSpan = blocks.first { it.text.contains("大号字") }.spansCovering("大号字").firstOrNull()
        assertNotNull("font size 应解析字号", sizeSpan?.sizeEm)
        assertTrue("u 应解析下划线", blocks.first { it.text.contains("下划线") }.spansCovering("下划线").first().underline)
        assertTrue("s 应解析删除线", blocks.first { it.text.contains("删除线") }.spansCovering("删除线").first().strike)

        val spanStyled = blocks.first { it.text.contains("span样式") }.spansCovering("span样式").first()
        assertEquals(0xFF008000.toInt(), spanStyled.color)
        assertTrue(spanStyled.bold)
    }

    @Test
    fun `quote block is marked as quote kind`() {
        val quote = parseFixture().textBlocks().firstOrNull { it.kind == TextKind.Quote }
        assertNotNull("引用块应单独成段并标记 Quote", quote)
        assertTrue(quote!!.text.contains("引用内容"))
    }

    @Test
    fun `text has no html leftovers and image order is kept`() {
        val blocks = parseFixture().posts.flatMap { it.blocks }
        val text = blocks.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
        assertTrue("正文不应残留标签", !text.contains("<font") && !text.contains("<div"))
        assertTrue("普通文字应保留", text.contains("普通文字"))
        assertTrue("尾部文字应保留", text.contains("尾部文字"))

        val imageIndex = blocks.indexOfFirst { it is ContentBlock.Image }
        assertTrue("正文图片应被识别", imageIndex >= 0)
        val secondIdx = blocks.indexOfFirst { it is ContentBlock.Text && it.text.contains("第二段") }
        val tailIdx = blocks.indexOfFirst { it is ContentBlock.Text && it.text.contains("尾部文字") }
        assertTrue("图片应位于第二段与尾部文字之间", secondIdx < imageIndex && imageIndex < tailIdx)
    }

    @Test
    fun `span bounds are always valid`() {
        parseFixture().textBlocks().forEach { block ->
            block.spans.forEach { span ->
                assertTrue("start 越界: $span in ${block.text.length}", span.start in 0..block.text.length)
                assertTrue("end 越界: $span", span.end in span.start..block.text.length)
            }
        }
    }

    @Test
    fun `real thread fixture keeps green span`() {
        val html = load("viewthread.html")
        val detail = ThreadDetailParser.parse(html, "https://mce.msi.cn/forum.php?mod=viewthread&tid=37839", 37839, 1)
        val spans = detail.posts.flatMap { it.blocks }.filterIsInstance<ContentBlock.Text>().flatMap { it.spans }
        assertTrue("真实帖子应解析出绿色样式", spans.any { it.color == 0xFF008000.toInt() })
    }
}
