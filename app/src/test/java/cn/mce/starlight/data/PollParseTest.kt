package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 帖子内投票解析（v0.3.3）。
 *
 * 真实抓包样本：
 *  - poll_thread_multi.html：多选投票（checkbox、max_obj=2、未投票）
 *  - poll_thread_voted.html：单选投票且已投过票（无 input、选项带 .poll_done、poll_note4 提示）
 */
class PollParseTest {

    private fun load(name: String): String {
        val candidates = listOf(
            File("src/test/resources/$name"),
            File("app/src/test/resources/$name"),
        )
        val file = candidates.firstOrNull { it.exists() } ?: error("fixture not found: $name")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun `multi choice poll is parsed with options and max choices`() {
        val detail = ThreadDetailParser.parse(load("poll_thread_multi.html"), "https://mce.msi.cn/forum.php?mod=viewthread&tid=38058", 38058, 1)
        val poll = detail.poll
        assertNotNull("应解析出投票", poll)
        poll!!
        assertTrue("应为多选", poll.multi)
        assertEquals(2, poll.maxChoices)
        assertEquals(2, poll.options.size)
        assertEquals("OptionAAA", poll.options[0].label)
        assertEquals("1851", poll.options[0].id)
        assertEquals(0, poll.options[0].votes)
        assertEquals("OptionBBB", poll.options[1].label)
        assertTrue("未投票时应能提交", !poll.voted)
        assertTrue("formhash 应解析", poll.formhash.isNotBlank())
        assertTrue("action 应包含 votepoll", poll.action.contains("action=votepoll"))
        assertTrue("查看参与人链接应解析", poll.viewVoteUrl.orEmpty().contains("action=viewvote"))
    }

    @Test
    fun `voted poll is read only with counts`() {
        val detail = ThreadDetailParser.parse(load("poll_thread_voted.html"), "https://mce.msi.cn/forum.php?mod=viewthread&tid=38060", 38060, 1)
        val poll = detail.poll
        assertNotNull("已投票的帖子也应解析出投票结果", poll)
        poll!!
        assertTrue("应判定为已投票", poll.voted)
        assertTrue("应为单选", !poll.multi)
        assertTrue("选项应带票数", poll.options.any { it.votes > 0 })
        assertTrue("单选选项 id 应可缺省（voted_ 前缀兜底）", poll.options.all { it.label.isNotBlank() })
    }

    @Test
    fun `non poll thread has no poll`() {
        val html = "<html><body><div class=\"message\">hello</div></body></html>"
        val detail = ThreadDetailParser.parse(html, "https://mce.msi.cn/forum.php?mod=viewthread&tid=1", 1, 1)
        assertNull(detail.poll)
    }

    // ---------- v0.3.7：投票显示重做的回归 ----------

    @Test
    fun `thread title comes from thname not section h1`() {
        val multi = ThreadDetailParser.parse(
            load("poll_thread_multi.html"),
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=38058", 38058, 1,
        )
        // 旧实现取 doc.selectFirst("h1")，而移动模板的 h1 是版块名「综合交流」
        assertEquals("aMCE_v032_poll_test", multi.title)

        val voted = ThreadDetailParser.parse(
            load("poll_thread_voted.html"),
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=38060", 38060, 1,
        )
        assertEquals("apptest_poll_v032", voted.title)
    }

    @Test
    fun `voted poll exposes totals and server percent`() {
        val poll = ThreadDetailParser.parse(
            load("poll_thread_voted.html"),
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=38060", 38060, 1,
        ).poll!!
        assertEquals(3, poll.totalVoters)
        assertEquals(3, poll.totalVotes)
        assertTrue("已投票不是已结束", !poll.closed)
        assertEquals(100f, poll.options.first().percent)
    }

    @Test
    fun `poll structure text does not leak into post content`() {
        val detail = ThreadDetailParser.parse(
            load("poll_thread_voted.html"),
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=38060", 38060, 1,
        )
        val text = detail.posts.flatMap { post -> post.blocks }
            .filterIsInstance<ContentBlock.Text>()
            .joinToString("\n") { it.text }
        // 旧实现只过滤 poll_*，ss_data / ss_line 会混进正文，首楼出现「45 浏览人数 3 投票人数…」残句
        assertTrue("正文不应含浏览人数", !text.contains("浏览人数"))
        assertTrue("正文不应含投票人数", !text.contains("投票人数"))
        assertTrue("正文不应含累计投票数", !text.contains("累计投票数"))
        assertTrue("正文不应含投票项目标题", !text.contains("投票项目"))
    }

    @Test
    fun `max_obj zero means unlimited choices`() {
        val html = """
            <html><body>
            <form id="poll" action="forum.php?mod=misc&action=votepoll&tid=9">
              <input type="hidden" name="formhash" value="abc"/>
              <div class="poll_text"><span class="poll_op_name">1. A</span>
                <input type="checkbox" name="pollanswers[]" value="11"/></div>
              <div class="poll_text"><span class="poll_op_name">2. B</span>
                <input type="checkbox" name="pollanswers[]" value="12"/></div>
            </form>
            <script>var max_obj = 0;</script>
            </body></html>
        """.trimIndent()
        val poll = ThreadDetailParser.parse(html, "https://mce.msi.cn/forum.php?mod=viewthread&tid=9", 9, 1).poll!!
        assertTrue(poll.multi)
        assertEquals("max_obj=0 表示不限项数", Int.MAX_VALUE, poll.maxChoices)
    }

    @Test
    fun `closed poll is not treated as voted`() {
        val html = """
            <html><body>
            <div class="poll_list">
              <div class="poll_text"><span class="poll_op_name poll_done">1. A</span></div>
            </div>
            <div class="poll_note4">投票已结束，感谢参与</div>
            </body></html>
        """.trimIndent()
        val poll = ThreadDetailParser.parse(html, "https://mce.msi.cn/forum.php?mod=viewthread&tid=9", 9, 1).poll!!
        assertTrue("已结束应标记 closed", poll.closed)
        assertTrue("已结束不能当成已投票（否则文案变『您已投过票』）", !poll.voted)
    }

    @Test
    fun `duplicate labels with different ids are kept`() {
        val html = """
            <html><body>
            <form id="poll" action="forum.php?mod=misc&action=votepoll&tid=9">
              <div class="poll_text"><span class="poll_op_name">1. AAA</span>
                <input type="radio" name="pollanswers[]" value="11"/></div>
              <div class="poll_text"><span class="poll_op_name">2. AAA</span>
                <input type="radio" name="pollanswers[]" value="12"/></div>
            </form>
            </body></html>
        """.trimIndent()
        val poll = ThreadDetailParser.parse(html, "https://mce.msi.cn/forum.php?mod=viewthread&tid=9", 9, 1).poll!!
        // 旧实现 distinctBy(label) 会把同名选项静默吞掉，导致提交时选项 id 少一个
        assertEquals(2, poll.options.size)
    }
}
