package cn.mce.starlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：私信正文里的网址识别（气泡内可点击，点击用系统浏览器打开）。
 */
class PmLinkifyTest {

    @Test
    fun findsHttpAndHttps() {
        val links = PmLinkify.find("看这个 https://mce.msi.cn/forum.php?mod=viewthread&tid=1 很好")
        assertEquals(1, links.size)
        assertEquals("https://mce.msi.cn/forum.php?mod=viewthread&tid=1", links[0].url)
        assertEquals("看这个 ".length, links[0].start)
        assertEquals("看这个 ".length + links[0].url.length, links[0].endExclusive)
    }

    @Test
    fun stripsTrailingChinesePunctuation() {
        val links = PmLinkify.find("地址是 https://example.com/a。谢谢")
        assertEquals(1, links.size)
        assertEquals("https://example.com/a", links[0].url)
    }

    @Test
    fun addsSchemeForWww() {
        val links = PmLinkify.find("www.example.com")
        assertEquals(1, links.size)
        assertEquals("https://www.example.com", links[0].url)
    }

    @Test
    fun handlesMultipleLinks() {
        val text = "A https://a.com/x B http://b.com/y?q=1 C"
        val links = PmLinkify.find(text)
        assertEquals(2, links.size)
        assertEquals("https://a.com/x", links[0].url)
        assertEquals("http://b.com/y?q=1", links[1].url)
    }

    @Test
    fun ignoresPlainText() {
        assertTrue(PmLinkify.find("今天天气不错，没有链接").isEmpty())
        assertFalse(PmLinkify.hasLinks("私信内容 12345"))
    }
}
