package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：非图片附件解析（zip / pdf / apk 等）。
 * 图片附件仍走内联图片渲染，不进附件列表。
 */
class AttachmentParseTest {

    private fun parse(messageHtml: String): ThreadDetail = ThreadDetailParser.parse(
        """
        <html><body>
          <div class="thname"><a href="#">附件测试帖</a></div>
          <div class="plc" id="pid810001">
            <div class="message">$messageHtml</div>
          </div>
        </body></html>
        """.trimIndent(),
        "https://mce.msi.cn/forum.php?mod=viewthread&tid=8&mobile=2",
        8,
        1,
    )

    @Test
    fun zipAttachment_isParsedWithSize() {
        val detail = parse(
            """
            <p>资料见附件</p>
            <p class="attnm">
              <a href="forum.php?mod=attachment&amp;aid=NzIwOTl8YWY2ZTA3YWN8MTc5MjkzMzM1NHwwfDE0ODkz">
                2026活动资料.zip
              </a>
              <em>(1.2 MB)</em>
            </p>
            """.trimIndent(),
        )
        val att = detail.attachments.firstOrNull()
        assertNotNull("应解析出附件", att)
        assertEquals("2026活动资料.zip", att!!.name)
        assertTrue("附件地址应为绝对地址", att.url.startsWith("https://mce.msi.cn/"))
        assertEquals("1.2 MB", att.sizeText)
    }

    @Test
    fun imageAttachment_isNotListedAsAttachment() {
        val detail = parse(
            """
            <p>
              <a href="forum.php?mod=attachment&amp;aid=abc&amp;size=3000x0">照片.jpg</a>
            </p>
            <p class="attnm">
              <a href="forum.php?mod=attachment&amp;aid=def">课件.pdf</a>
            </p>
            """.trimIndent(),
        )
        assertEquals("图片附件不应出现在附件列表", 1, detail.attachments.size)
        assertEquals("课件.pdf", detail.attachments.first().name)
    }

    @Test
    fun noAttachment_returnsEmptyList() {
        val detail = parse("<p>纯文字帖子，没有附件</p>")
        assertTrue(detail.attachments.isEmpty())
    }
}
