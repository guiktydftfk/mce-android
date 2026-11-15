package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：消息提醒里的「好友请求」解析与分类筛选支撑。
 *
 * 提醒条目里如果带 `ac=friend&op=add` / `op=ignore` 链接，
 * 说明是好友请求，可以直接在提醒列表里同意 / 拒绝。
 */
class NoticeFriendRequestParseTest {

    private val html = """
        <html><body><ul>
          <li class="notice_item">
            <a class="avatars" href="home.php?mod=space&amp;uid=4146">
              <img src="uc_server/avatar.php?uid=4146&amp;size=small">
            </a>
            <p class="notice_time">10 分钟前</p>
            <div class="notice_note">
              流云闲鹿 请求加你为好友
              <a href="home.php?mod=spacecp&amp;ac=friend&amp;op=add&amp;fuid=4146&amp;confirm=abc&amp;formhash=f1">同意</a>
              <a href="home.php?mod=spacecp&amp;ac=friend&amp;op=ignore&amp;fuid=4146&amp;formhash=f1">拒绝</a>
            </div>
          </li>
          <li class="notice_item">
            <div class="notice_note">
              <a href="forum.php?mod=redirect&amp;goto=findpost&amp;ptid=45&amp;pid=123">回复了你的帖子</a>
            </div>
          </li>
        </ul></body></html>
    """.trimIndent()

    private fun parse() = PersonalClient.parseNoticeItems(
        org.jsoup.Jsoup.parse(html, "https://mce.msi.cn/home.php?mod=space&do=notice"),
    )

    @Test
    fun friendRequestNotice_hasAcceptAndIgnoreLinks() {
        val item = parse().first()
        assertNotNull("应解析出接受好友请求的链接", item.acceptUrl)
        assertNotNull("应解析出拒绝好友请求的链接", item.ignoreUrl)
        assertTrue("接受链接应走 ac=friend&op=add", item.acceptUrl!!.contains("ac=friend"))
        assertTrue("接受链接应包含 op=add", item.acceptUrl!!.contains("op=add"))
        assertTrue("拒绝链接应包含 op=ignore", item.ignoreUrl!!.contains("op=ignore"))
        assertEquals(4146, item.friendUid)
        assertTrue("链接应为绝对地址", item.acceptUrl!!.startsWith("https://mce.msi.cn/"))
    }

    @Test
    fun ordinaryNotice_hasNoFriendAction() {
        val items = parse()
        val normal = items[1]
        assertNull(normal.acceptUrl)
        assertNull(normal.ignoreUrl)
        assertEquals(45, normal.tid)
        assertEquals(123, normal.pid)
    }
}
