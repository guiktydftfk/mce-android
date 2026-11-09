package cn.mce.starlight.data

import cn.mce.starlight.core.PmReadStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.5：补充回归测试（目标：单测总数 ≥120）。
 * 覆盖 TA 主题解析、评分解析、私信已读签名容错的边界情况。
 */
class ParsingEdgeCaseTest {

    // ---------- TA 主题：解析边界 ----------

    private fun threadRow(tid: Int, title: String, board: String = "综合交流", replies: Int = 3): String =
        """
        <tr>
          <th><a href="forum.php?mod=viewthread&amp;tid=$tid">$title</a></th>
          <td><a href="forum.php?mod=forumdisplay&amp;fid=2" class="xg1">$board</a></td>
          <td class="num"><a class="xi2">$replies</a><em>10</em></td>
          <td class="by"><cite><a>测试用户</a></cite><em><a>2026-01-01 10:00</a></em></td>
        </tr>
        """

    private fun wrap(rows: String): String =
        """<html><body><div class="tl"><table><tr class="th"><th>主题</th></tr>$rows</table></div></body></html>"""

    @Test
    fun userThreads_emptyHtml_returnsEmpty() {
        assertTrue(UserThreadsClient.parse("<html></html>").isEmpty())
    }

    @Test
    fun userThreads_headerOnly_returnsEmpty() {
        assertTrue(UserThreadsClient.parse(wrap("")).isEmpty())
    }

    @Test
    fun userThreads_singleRow_parsed() {
        val items = UserThreadsClient.parse(wrap(threadRow(100, "测试主题")))
        assertEquals(1, items.size)
        assertEquals(100, items[0].tid)
        assertEquals("测试主题", items[0].title)
        assertEquals("综合交流", items[0].board)
        assertEquals(3, items[0].replies)
    }

    @Test
    fun userThreads_multipleRows_dedupByTid() {
        val items = UserThreadsClient.parse(wrap(threadRow(1, "第一帖") + threadRow(1, "重复帖") + threadRow(2, "第二帖")))
        assertEquals(2, items.size)
        assertEquals(listOf(1, 2), items.map { it.tid })
    }

    @Test
    fun userThreads_navLinkOutsideTable_ignored() {
        val html = """<html><body><a href="forum.php?mod=viewthread&tid=9">常用表单</a>
            <div class="tl"><table>${threadRow(5, "正常主题")}</table></div></body></html>"""
        val items = UserThreadsClient.parse(html)
        assertEquals(1, items.size)
        assertEquals(5, items[0].tid)
    }

    // ---------- 评分解析边界 ----------

    private fun rateHtml(count: Int, total: String, entries: String): String =
        """
        <dl class="rate"><dd><table class="ratl">
        <tr><th><a href="misc.php?action=viewratings">参与人数 <span class="xi1">$count</span></a>
        赞赏值 <i><span class="xi1">$total</span></i></th></tr>
        <tbody class="ratl_l">$entries</tbody></table></dd></dl>
        """

    private fun rateEntry(uid: Int, name: String, amount: String, reason: String = ""): String =
        """<tr><td class="xi1">$amount</td><td class="xg1">$reason</td>
        <td><a href="home.php?mod=space&amp;uid=$uid">$name</a></td></tr>"""

    @Test
    fun ratings_noBlock_returnsNull() {
        assertNull(ThreadDetailParser.parseRatingsFromHtml("<html></html>", Net.BASE))
    }

    @Test
    fun ratings_blockWithoutEntries_returnsNull() {
        assertNull(ThreadDetailParser.parseRatingsFromHtml(rateHtml(0, "", ""), Net.BASE))
    }

    @Test
    fun ratings_singleEntry() {
        val r = ThreadDetailParser.parseRatingsFromHtml(rateHtml(1, "+2", rateEntry(1, "甲", "+2", "很好")), Net.BASE)
        assertNotNull(r)
        assertEquals(1, r!!.entries.size)
        assertEquals("甲", r.entries[0].name)
        assertEquals("+2", r.entries[0].amount)
        assertEquals("很好", r.entries[0].reason)
    }

    @Test
    fun ratings_multipleEntriesOrderKept() {
        val r = ThreadDetailParser.parseRatingsFromHtml(
            rateHtml(3, "+6", rateEntry(1, "甲", "+2") + rateEntry(2, "乙", "+2") + rateEntry(3, "丙", "+2")),
            Net.BASE,
        )
        assertNotNull(r)
        assertEquals(listOf("甲", "乙", "丙"), r!!.entries.map { it.name })
    }

    @Test
    fun ratings_reasonBlank_storedAsNull() {
        val r = ThreadDetailParser.parseRatingsFromHtml(rateHtml(1, "+1", rateEntry(7, "丁", "+1", "")), Net.BASE)
        assertNotNull(r)
        assertNull(r!!.entries[0].reason)
    }

    // ---------- 私信已读签名边界 ----------

    @Test
    fun pmRead_storedNull_false() = assertFalse(PmReadStore.matches(null, "任意"))
    @Test
    fun pmRead_storedBlank_false() = assertFalse(PmReadStore.matches("", "任意"))
    @Test
    fun pmRead_currentBlank_false() = assertFalse(PmReadStore.matches("内容", ""))
    @Test
    fun pmRead_equalWithWhitespace_true() = assertTrue(PmReadStore.matches("你好  世界", "你好 世界"))
    @Test
    fun pmRead_truncatedPreview_true() = assertTrue(PmReadStore.matches("今天下午三点集合，记得带外套", "今天下午三点..."))
    @Test
    fun pmRead_newLongerMessage_false() = assertFalse(PmReadStore.matches("好的", "好的，我马上到"))
    @Test
    fun pmRead_singleCharNoMatch() = assertFalse(PmReadStore.matches("啊", "啊呀"))
}
