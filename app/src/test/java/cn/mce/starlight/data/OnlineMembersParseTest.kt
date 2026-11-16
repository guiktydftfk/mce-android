package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：在线会员 / 论坛在线统计解析（电脑版 forum.php?mod=online）。
 */
class OnlineMembersParseTest {

    private val html = """
        <html><body>
          <div id="ct">
            <h2>在线会员 - 共 12 人在线 - 8 会员(0 隐身), 4 位游客</h2>
            <div class="onlinelist">
              <a href="home.php?mod=space&amp;uid=4146">流云闲鹿</a>
              <a href="home.php?mod=space&amp;uid=2">测试用户</a>
              <a href="member.php?mod=logging&amp;action=login">登录</a>
            </div>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun onlinePage_parsesCounts() {
        val page = OnlineClient.parse(html, "https://mce.msi.cn/forum.php?mod=online")
        assertEquals(12, page.total)
        assertEquals(8, page.memberCount)
        assertEquals(4, page.guestCount)
        assertTrue("标题应包含在线会员字样", page.caption.contains("在线"))
    }

    @Test
    fun onlinePage_parsesMembersAndSkipsLoginLink() {
        val page = OnlineClient.parse(html, "https://mce.msi.cn/forum.php?mod=online")
        assertEquals(2, page.members.size)
        assertEquals("流云闲鹿", page.members[0].name)
        assertEquals(4146, page.members[0].uid)
        assertNotNull("应生成头像地址", page.members[0].avatar)
        assertTrue(page.members[0].avatar!!.contains("uid=4146"))
    }

    @Test
    fun emptyPage_returnsEmptyMembersWithoutCrash() {
        val page = OnlineClient.parse("<html><body>暂无数据</body></html>", "https://mce.msi.cn/")
        assertTrue(page.members.isEmpty())
        assertEquals("在线会员", page.caption)
        assertEquals(null, page.total)
    }
}
