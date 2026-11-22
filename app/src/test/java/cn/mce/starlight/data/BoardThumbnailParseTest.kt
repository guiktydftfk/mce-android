package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：板块列表缩略图解析。
 *
 * 肇庆等图片板块此前很慢：列表只给帖子 id，缩略图要逐帖再抓一次完整帖子页解析首图。
 * 实际论坛手机模板列表页已经内嵌了 `img[data-src]`（200x180 小图），
 * 直接使用可省掉每帖一次整页请求。
 */
class BoardThumbnailParseTest {

    private val html = """
        <html><body><ul id="alist" class="pagelist">
          <li class="subjects_3 mtb">
            <div class="th_authi">
              <a href="home.php?mod=space&amp;uid=1347" class="avatar"><img src="avatar.php?uid=1347"/></a>
              <span class="th_author"><a href="#">作者A</a></span>
              <span class="y"> 2024-5-17 <span class="pipe">/</span> 4回复 </span>
            </div>
            <a href="forum.php?mod=viewthread&amp;tid=21371&amp;extra=page%3D1" class="th_item">
              <h1><span>带图的帖子</span></h1>
              <p>摘要…</p>
              <div class="imgbox3">
                <div><img class="lazys" src="static/image/common/none.gif"
                          data-src="forum.php?mod=image&amp;aid=101651&amp;size=200x180&amp;key=84687d6cc0913b83&amp;?ZB8" /></div>
                <div><img class="lazys" src="static/image/common/none.gif"
                          data-src="forum.php?mod=image&amp;aid=101652&amp;size=200x180&amp;key=0acb1b121156a4ed&amp;?ZB8" /></div>
              </div>
            </a>
          </li>
          <li class="subjects_3 mtb">
            <div class="th_authi"><span class="th_author"><a href="#">作者B</a></span>
              <span class="y"> 3小时前 / 1回复 </span></div>
            <a href="forum.php?mod=viewthread&amp;tid=21372" class="th_item"><h1>纯文字帖</h1></a>
          </li>
        </ul></body></html>
    """.trimIndent()

    @Test
    fun listThumbnail_isParsedAndCleaned() {
        val page = MobileForumParser.parse(html, "https://mce.msi.cn/forum.php?mod=forumdisplay&fid=68", 1)
        assertEquals(2, page.threads.size)

        val withImage = page.threads.first { it.id == 21371 }
        assertNotNull("应解析出列表缩略图", withImage.thumbUrl)
        val url = withImage.thumbUrl!!
        assertTrue(url.startsWith("https://mce.msi.cn/forum.php?mod=image"))
        assertTrue("应带 aid", url.contains("aid=101651"))
        assertTrue("应清理 &?ZB8 版本尾巴", !url.contains("&?ZB8"))

        val textOnly = page.threads.first { it.id == 21372 }
        assertEquals(null, textOnly.thumbUrl)
    }
}
