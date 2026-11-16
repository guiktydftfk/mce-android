package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：活动 / 悬赏 / 交易帖识别（只读信息展示，参与走网页兜底）。
 */
class SpecialThreadParseTest {

    private fun parse(html: String): ThreadDetail = ThreadDetailParser.parse(
        html,
        "https://mce.msi.cn/forum.php?mod=viewthread&tid=77",
        77,
        1,
    )

    private fun wrap(messageHtml: String): String = """
        <html><body>
          <div class="thname"><a href="#">特殊帖测试</a></div>
          <div class="plc" id="pid990001"><div class="message">$messageHtml</div></div>
        </body></html>
    """.trimIndent()

    @Test
    fun activityThread_isDetected() {
        val html = wrap(
            """
            <p>活动时间：2026-12-01 19:00</p>
            <p>活动地点：线上腾讯会议</p>
            <p>已报名 12 人，欢迎大家报名参加活动</p>
            <a href="home.php?mod=spacecp&amp;ac=activity&amp;op=apply&amp;tid=77">我要报名</a>
            """.trimIndent(),
        )
        val special = parse(html).special
        assertNotNull("应识别为活动帖", special)
        assertEquals(SpecialKind.Activity, special!!.kind)
        assertTrue("应提取活动时间", special.lines.any { it.contains("活动时间") || it.contains("2026-12-01") })
    }

    @Test
    fun bountyThread_isDetected() {
        val html = wrap(
            """
            <p>悬赏 500 星光值求一个完整方案</p>
            <p>状态：未解决</p>
            """.trimIndent(),
        )
        val special = parse(html).special
        assertNotNull(special)
        assertEquals(SpecialKind.Bounty, special!!.kind)
    }

    @Test
    fun tradeThread_isDetected() {
        val html = wrap(
            """
            <p>出售：全新星宝周边一套</p>
            <p>价格：200 星光值</p>
            """.trimIndent(),
        )
        val special = parse(html).special
        assertNotNull(special)
        assertEquals(SpecialKind.Trade, special!!.kind)
    }

    @Test
    fun normalThread_hasNoSpecial() {
        val html = wrap("<p>普通聊天帖，今天天气不错</p>")
        assertNull(parse(html).special)
    }
}
