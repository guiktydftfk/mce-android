package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.4.7：标题 HTML 实体二次解码（站点偶发双重转义，如 &#129402; 应显示为 🥺 而不是字符原文） */
class HtmlEntityTitleTest {

    private fun pageWithTitle(titleHtml: String): String =
        """
        <html><body><ul>
          <li class="subjects_1 mtb"><span class="y">1 小时前 / 3 回复</span>
            <a class="th_item" href="forum.php?mod=viewthread&tid=1"><h1>$titleHtml</h1></a>
          </li>
        </ul></body></html>
        """.trimIndent()

    @Test
    fun numericEntity_decoded() {
        val page = MobileForumParser.parse(pageWithTitle("七年了&amp;#129402;"), "https://mce.msi.cn/", 1)
        val title = page.threads.first().title
        assertEquals("七年了🥺", title)
    }

    @Test
    fun plainTitle_untouched() {
        val page = MobileForumParser.parse(pageWithTitle("普通标题"), "https://mce.msi.cn/", 1)
        assertEquals("普通标题", page.threads.first().title)
    }
}
