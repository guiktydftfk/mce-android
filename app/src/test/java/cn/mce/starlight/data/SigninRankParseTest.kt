package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.3.2 回归：签到排行榜「显示不完整」。
 *
 * 旧实现解析 `table#misign_list tr` 后 `take(30)`，而今日榜有 146+ 人、
 * 本月/总榜每页 200 人且有多页 —— 榜单只剩前 30 条。
 * 这里用真实抓取的第一页（191 条）验证：全部条目都要解析出来。
 */
class SigninRankParseTest {

    private fun load(name: String): String {
        val candidates = listOf(
            File("src/test/resources/$name"),
            File("app/src/test/resources/$name"),
        )
        val file = candidates.firstOrNull { it.exists() } ?: error("fixture not found: $name")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun `all rank rows are parsed (no 30 item cap)`() {
        val html = load("mobile-live/plugin_php_id_k_misign_sign_operation_list.html")
        val entries = SigninClient.parseRankEntries(html)
        println("rank entries = ${entries.size}")
        assertTrue("应解析出全部 191 条，实际 ${entries.size}", entries.size >= 190)

        val first = entries.first()
        println("first: $first")
        assertEquals("肉丸鸭鸭", first.username)
        assertEquals(4146, first.uid)
        assertEquals(183, first.totalDays)
        assertEquals(19, first.monthDays)
        assertEquals("2", first.reward)
        assertTrue("签到时间应解析，实际 ${first.lastSignText}", first.lastSignText.orEmpty().contains("前"))
    }

    @Test
    fun `username with special characters is preserved`() {
        val html = load("mobile-live/plugin_php_id_k_misign_sign_operation_list.html")
        val entries = SigninClient.parseRankEntries(html)
        assertTrue(
            "包含 @ 的用户名应原样解析",
            entries.any { it.username.startsWith("@") },
        )
        assertTrue("不应有空用户名", entries.all { it.username.isNotBlank() })
        assertTrue("uid 不应重复", entries.map { it.uid }.distinct().size == entries.size)
    }

    /**
     * 真机回归（v0.3.2 实测发现）：`format=ajax` 的排行榜响应**没有外层 table**，
     * 直接是 `<tbody id="autolist_xxx">` 片段；Jsoup 按 HTML 规则解析会把游离的
     * tbody/tr 丢掉 → 解析出 0 条，页面显示「暂无排行榜数据」。
     */
    @Test
    fun `ajax fragment without table wrapper is parsed`() {
        val html = load("signin_rank_ajax_fragment.html")
        assertTrue("ajax 片段不应包含 <table>", !html.contains("<table", ignoreCase = true))
        val entries = SigninClient.parseRankEntries(html)
        println("ajax fragment entries = ${entries.size}")
        assertTrue("ajax 片段应解析出 100+ 条，实际 ${entries.size}", entries.size >= 100)
        assertTrue("首条应有 uid", entries.first().uid != null)
        assertTrue("首条应有总天数", entries.first().totalDays != null)
    }

    @Test
    fun `second page fragment is parsed`() {
        val html = load("signin_rank_page2_fragment.html")
        val entries = SigninClient.parseRankEntries(html)
        println("page2 entries = ${entries.size}")
        assertTrue("第二页片段应解析出 100+ 条，实际 ${entries.size}", entries.size >= 100)
    }
}
