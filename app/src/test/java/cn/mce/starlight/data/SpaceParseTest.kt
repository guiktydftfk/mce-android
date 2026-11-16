package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：个人空间扩展解析（日志 / 相册 / 留言板 / 访客）。
 */
class SpaceParseTest {

    @Test
    fun blogs_parsedWithDate() {
        val html = """
            <html><body><ul>
              <li><a href="home.php?mod=space&amp;uid=1&amp;do=blog&amp;id=101">我的第一篇日志</a>
                  <span>2026-01-02 10:00</span></li>
              <li><a href="home.php?mod=space&amp;uid=1&amp;do=blog&amp;id=102">第二篇日志</a>
                  <span>2026-01-03</span></li>
            </ul></body></html>
        """.trimIndent()
        val list = SpaceClient.parseBlogs(html)
        assertEquals(2, list.size)
        assertEquals("101", list[0].id)
        assertEquals("我的第一篇日志", list[0].title)
        assertEquals("2026-01-02 10:00", list[0].date)
    }

    @Test
    fun albums_parsedWithCoverAndCount() {
        val html = """
            <html><body><ul>
              <li>
                <a href="home.php?mod=space&amp;uid=1&amp;do=album&amp;id=55">
                  <img src="data/attachment/album/cover55.jpg">旅行相册
                </a>
                <span>12 张</span>
              </li>
            </ul></body></html>
        """.trimIndent()
        val list = SpaceClient.parseAlbums(html)
        assertEquals(1, list.size)
        assertEquals("55", list[0].id)
        assertEquals("旅行相册", list[0].title)
        assertNotNull(list[0].cover)
        assertTrue(list[0].cover!!.startsWith("https://mce.msi.cn/"))
        assertEquals("12 张", list[0].countText)
    }

    @Test
    fun wall_parsedAuthorTextTime() {
        val html = """
            <html><body><ul class="comment_list">
              <li>
                <a href="home.php?mod=space&amp;uid=2">小明</a>
                生日快乐，天天开心
                <em>2026-01-02 10:00</em>
              </li>
            </ul></body></html>
        """.trimIndent()
        val list = SpaceClient.parseWall(html)
        assertEquals(1, list.size)
        assertEquals("小明", list[0].author)
        assertTrue(list[0].text.contains("生日快乐"))
        assertEquals("2026-01-02 10:00", list[0].time)
    }

    @Test
    fun visitors_parsedUidAndName() {
        val html = """
            <html><body><ul class="visitors">
              <li>
                <a href="home.php?mod=space&amp;uid=9"><img src="uc_server/avatar.php?uid=9&amp;size=small"></a>
                <a href="home.php?mod=space&amp;uid=9">小九</a>
                <em>1 小时前</em>
              </li>
            </ul></body></html>
        """.trimIndent()
        val list = SpaceClient.parseVisitors(html)
        assertEquals(1, list.size)
        assertEquals(9, list[0].uid)
        assertEquals("小九", list[0].name)
        assertTrue(list[0].avatar!!.startsWith("https://mce.msi.cn/"))
    }

    @Test
    fun emptyPages_returnEmptyLists() {
        val html = "<html><body>暂无内容</body></html>"
        assertTrue(SpaceClient.parseBlogs(html).isEmpty())
        assertTrue(SpaceClient.parseAlbums(html).isEmpty())
        assertTrue(SpaceClient.parseWall(html).isEmpty())
        assertTrue(SpaceClient.parseVisitors(html).isEmpty())
    }
}
