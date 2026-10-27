package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.3.10：管理身份识别 + 楼层/回复数语义回归。
 *
 * 1. 「我的」页按用户组显示 管理员 / 超级版主 / 版主 徽标，普通用户不显示；
 *    不能因为页面正文里出现「版主」二字就误判（只看用户组字段和身份徽标元素）。
 * 2. 评论数要和列表卡片一致：楼主显示「楼主」，回复从 1楼 起编号；
 *    ThreadDetail.replies 取自页面 `N回复`，不再恒为 null。
 */
class ProfileStaffAndFloorTest {

    @Test
    fun staffTitle_adminFromBadge() {
        val doc = org.jsoup.Jsoup.parse(
            """<div class="userstatus"><i class="mod">管理员</i></div>""",
        )
        assertEquals("管理员", ProfileCommon.staffTitle(doc, doc.text()))
    }

    @Test
    fun staffTitle_superModFromGroup() {
        val doc = org.jsoup.Jsoup.parse("""<div class="grouptitle">超级版主</div>""")
        assertEquals("超级版主", ProfileCommon.staffTitle(doc, doc.text()))
    }

    @Test
    fun staffTitle_modFromGroupText() {
        val doc = org.jsoup.Jsoup.parse("""<p>用户组：版主</p>""")
        assertEquals("版主", ProfileCommon.staffTitle(doc, doc.text()))
    }

    @Test
    fun staffTitle_normalUserReturnsNull() {
        val doc = org.jsoup.Jsoup.parse("""<div class="grouptitle">S4 有爱星</div><p>用户组：S4</p>""")
        assertNull(ProfileCommon.staffTitle(doc, doc.text()))
    }

    @Test
    fun staffTitle_ignoresMentionsInContent() {
        // 帖子/签名里出现「版主」不应把人误判成版主
        val doc = org.jsoup.Jsoup.parse(
            """<div class="grouptitle">S4 有爱星</div>
               <div class="message">有事请联系版主，不要私聊管理员</div>""",
        )
        assertNull(ProfileCommon.staffTitle(doc, doc.text()))
    }

    @Test
    fun threadDetail_floorLabelsAndReplyCount() {
        val html = """
            <html><body>
            <div class="titls" id="ap_reply"><span class="z">6回复</span></div>
            <div class="plc bbno" id="pid1"><div class="view_authi"><a href="home.php?mod=space&uid=1">楼主甲</a></div>
                <div class="message">楼主正文</div></div>
            <div class="plc" id="pid2"><div class="view_authi"><a href="home.php?mod=space&uid=2">回复乙</a></div>
                <div class="message">第一条回复</div></div>
            <div class="plc" id="pid3"><div class="view_authi"><a href="home.php?mod=space&uid=3">回复丙</a></div>
                <div class="message">第二条回复</div></div>
            </body></html>
        """.trimIndent()
        val d = ThreadDetailParser.parse(html, "https://mce.msi.cn/forum.php", 38074, 1)
        assertEquals(6, d.replies)
        assertEquals(3, d.posts.size)
        assertEquals("楼主", d.posts[0].floorLabel)
        assertEquals("1楼", d.posts[1].floorLabel)
        assertEquals("2楼", d.posts[2].floorLabel)
    }

    @Test
    fun threadDetail_linkSpansRecorded() {
        val html = """
            <html><body>
            <div class="plc bbno" id="pid1"><div class="view_authi"><a href="home.php?mod=space&uid=1">甲</a></div>
                <div class="message">下载地址 <a href="https://example.com/apk">点这里</a> 谢谢</div></div>
            </body></html>
        """.trimIndent()
        val d = ThreadDetailParser.parse(html, "https://mce.msi.cn/forum.php", 1, 1)
        val textBlock = d.posts[0].blocks.filterIsInstance<ContentBlock.Text>().first()
        assertEquals(1, textBlock.links.size)
        val link = textBlock.links.first()
        assertEquals("https://example.com/apk", link.url)
        assertEquals("点这里", textBlock.text.substring(link.start, link.end))
    }
}
