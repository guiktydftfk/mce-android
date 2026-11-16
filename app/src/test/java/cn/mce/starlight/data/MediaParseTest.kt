package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：帖子媒体内容解析（视频 / 音频 / 嵌入 iframe / 残留 [media] BBCode）。
 */
class MediaParseTest {

    private fun parse(messageHtml: String): Post {
        val html = """
            <html><body>
              <div class="thname"><a href="#">媒体测试帖</a></div>
              <div class="plc" id="pid920001">
                <div class="message">$messageHtml</div>
              </div>
            </body></html>
        """.trimIndent()
        val detail = ThreadDetailParser.parse(
            html,
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=1",
            1,
            1,
        )
        assertEquals(1, detail.posts.size)
        return detail.posts.first()
    }

    @Test
    fun iframeVideo_becomesMediaVideo() {
        val post = parse(
            """<p>看这个视频：</p>
               <iframe src="https://v.qq.com/txp/iframe/player.html?vid=abc"></iframe>""",
        )
        val media = post.blocks.filterIsInstance<ContentBlock.Media>().firstOrNull()
        assertEquals(true, media != null)
        assertEquals(MediaKind.Video, media!!.kind)
        assertTrue(media.url.contains("v.qq.com"))
    }

    @Test
    fun audioTag_becomesMediaAudio() {
        val post = parse("""<audio src="https://mce.msi.cn/data/audio/demo.mp3" controls></audio>""")
        val media = post.blocks.filterIsInstance<ContentBlock.Media>().firstOrNull()
        assertEquals(true, media != null)
        assertEquals(MediaKind.Audio, media!!.kind)
    }

    @Test
    fun rawMediaBbcode_isExtracted() {
        val post = parse("""<p>视频：</p><p>[media=x,500,375]https://www.bilibili.com/video/BV1xx411c7mD[/media]</p>""")
        val media = post.blocks.filterIsInstance<ContentBlock.Media>().firstOrNull()
        assertEquals(true, media != null)
        assertTrue(media!!.url.contains("bilibili"))
        assertTrue("媒体块不应把 BBCode 当普通正文", post.blocks.filterIsInstance<ContentBlock.Text>().none { it.text.contains("[media") })
    }

    @Test
    fun plainText_hasNoMedia() {
        val post = parse("<p>今天天气不错，没有视频也没有音频</p>")
        assertTrue(post.blocks.filterIsInstance<ContentBlock.Media>().isEmpty())
    }
}
