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
}
