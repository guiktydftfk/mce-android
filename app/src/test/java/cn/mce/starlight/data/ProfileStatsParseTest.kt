package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.4.7：资料页统计数字解析回归测试。
 * 背景：用户反馈网页端积分 13661，App 显示 1（整页正则先命中了页面里别处的「积分 1」）。
 */
class ProfileStatsParseTest {

    @Test
    fun numberAfterLabel_plain() {
        assertEquals("6716", ProfileCommon.numberAfterLabel("积分 6716", "积分"))
    }

    @Test
    fun numberAfterLabel_withColon() {
        assertEquals("13661", ProfileCommon.numberAfterLabel("积分：13661", "积分"))
    }

    @Test
    fun numberAfterLabel_thousandsSeparator() {
        assertEquals("13661", ProfileCommon.numberAfterLabel("积分 13,661", "积分"))
    }

    @Test
    fun numberAfterLabel_wanUnit() {
        assertEquals("13000", ProfileCommon.numberAfterLabel("积分 1.3万", "积分"))
    }

    @Test
    fun numberAfterLabel_wrongLabel_returnsNull() {
        assertNull(ProfileCommon.numberAfterLabel("回帖 12", "积分"))
    }

    @Test
    fun statsFromDoc_prefersRealLargeValue() {
        // 页面别处有「积分 1 分」的说明文本，统计区真实值是 13661
        val html = """
            <html><body>
              <div class="tip"><span>积分 1 分</span></div>
              <ul class="user_stats">
                <li><i class="vt-gift"></i><p>积分 13661</p></li>
                <li><p>主题 114</p></li>
              </ul>
            </body></html>
        """.trimIndent()
        val doc = org.jsoup.Jsoup.parse(html)
        val stats = ProfileCommon.statsFromDoc(doc, listOf("主题", "积分"))
        assertEquals("13661", stats["积分"])
        assertEquals("114", stats["主题"])
    }

    @Test
    fun statsFromDoc_newUserSmallValueStillWorks() {
        val html = """<html><body><ul><li><p>积分 1</p></li></ul></body></html>"""
        val doc = org.jsoup.Jsoup.parse(html)
        assertEquals("1", ProfileCommon.statsFromDoc(doc, listOf("积分"))["积分"])
    }
}
