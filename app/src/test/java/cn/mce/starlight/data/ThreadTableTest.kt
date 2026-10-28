package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.0：含表格帖子的阅读体验。
 *
 * 旧实现把 `<tr>/<td>` 当普通文字节点摊平，表格在手机上变成一长串没有列的句子；
 * 现在表格解析成 [ContentBlock.Table]（按行/列），由详情页渲染成可横向滚动的表格。
 */
class ThreadTableTest {

    private fun parseWith(messageHtml: String): Post {
        val html = """
            <html><body>
              <div class="thname"><a href="#">表格测试帖</a></div>
              <div class="plc" id="pid900001">
                <div class="message">$messageHtml</div>
              </div>
            </body></html>
        """.trimIndent()
        val detail = ThreadDetailParser.parse(
            html,
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=1",
            1,
            1,
        )
        assertEquals(1, detail.posts.size)
        return detail.posts.first()
    }

    @Test
    fun table_becomesTableBlockWithHeader() {
        val post = parseWith(
            """
            <p>下面是一个表格：</p>
            <table>
              <tr><th>模块</th><th>说明</th></tr>
              <tr><td>首页</td><td>卡片式信息流</td></tr>
              <tr><td>发帖</td><td>BBCode 工具栏</td></tr>
            </table>
            """.trimIndent(),
        )
        val table = post.blocks.filterIsInstance<ContentBlock.Table>().firstOrNull()
        assertNotNull("表格应解析成 ContentBlock.Table", table)
        assertEquals(3, table!!.rows.size)
        assertEquals(listOf("模块", "说明"), table.rows[0])
        assertEquals(listOf("首页", "卡片式信息流"), table.rows[1])
        assertEquals(listOf("发帖", "BBCode 工具栏"), table.rows[2])
        assertTrue(table.hasHeader)
    }

    @Test
    fun table_cellsAreNotFlattenedIntoParagraphText() {
        val post = parseWith(
            """
            <table>
              <tr><th>模块</th><th>说明</th></tr>
              <tr><td>首页</td><td>卡片式信息流</td></tr>
            </table>
            """.trimIndent(),
        )
        val texts = post.blocks.filterIsInstance<ContentBlock.Text>()
            .joinToString("\n") { it.text }
        assertFalse("表格内容不应再以普通段落文字出现", texts.contains("卡片式信息流"))
    }

    @Test
    fun table_withoutTh_hasNoHeader() {
        val post = parseWith(
            """
            <table>
              <tr><td>甲</td><td>乙</td></tr>
              <tr><td>1</td><td>2</td></tr>
            </table>
            """.trimIndent(),
        )
        val table = post.blocks.filterIsInstance<ContentBlock.Table>().first()
        assertEquals(2, table.rows.size)
        assertFalse(table.hasHeader)
    }

    @Test
    fun table_keepsSurroundingTextOrder() {
        val post = parseWith(
            """
            <p>表格前</p>
            <table><tr><td>A</td><td>B</td></tr></table>
            <p>表格后</p>
            """.trimIndent(),
        )
        val blocks = post.blocks
        val tableIndex = blocks.indexOfFirst { it is ContentBlock.Table }
        assertEquals(1, tableIndex)
        val before = blocks[0] as ContentBlock.Text
        val after = blocks[2] as ContentBlock.Text
        assertTrue(before.text.contains("表格前"))
        assertTrue(after.text.contains("表格后"))
    }
}
