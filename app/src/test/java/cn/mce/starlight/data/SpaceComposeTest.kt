package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：个人空间「日志发布」表单解析（spacecp&ac=blog 桌面模板）。
 */
class SpaceComposeTest {

    private val html = """
        <html><body>
          <form id="blogform" action="home.php?mod=spacecp&ac=blog&mobile=no" method="post">
            <input type="hidden" name="formhash" value="abcd1234">
            <input type="text" id="subject" name="subject" value="">
            <select name="classid">
              <option value="0">无分类</option>
              <option value="27" selected>随笔</option>
            </select>
            <textarea id="message" name="message" rows="10"></textarea>
            <button type="submit" name="blogsubmit" value="true">发表</button>
          </form>
        </body></html>
    """.trimIndent()

    @Test
    fun blogForm_parsedWithActionAndFields() {
        val form = SpaceComposeClient.parseBlogForm(html)
        assertNotNull("应解析出日志表单", form)
        assertTrue(form!!.action.startsWith("https://mce.msi.cn/"))
        assertEquals("post", form.method.lowercase())
        assertTrue("应包含标题字段", form.fields.any { it.name == "subject" })
        val message = form.fields.firstOrNull { it.name == "message" }
        assertNotNull("应包含正文字段", message)
        assertTrue("正文应为多行", message!!.multiline)
        assertTrue("应包含 formhash 隐藏字段", form.fields.any { it.name == "formhash" && it.value == "abcd1234" })
        val select = form.fields.firstOrNull { it.name == "classid" }
        assertNotNull(select)
        assertTrue(select!!.options.isNotEmpty())
        assertEquals("27", select.value)
    }

    @Test
    fun formWithoutMessage_returnsNull() {
        val bad = """<html><body><form action="/x"><input name="subject" value=""></form></body></html>"""
        assertNull(SpaceComposeClient.parseBlogForm(bad))
    }

    @Test
    fun albumPageUrl_isDesktopSpacecp() {
        val url = SpaceComposeClient.albumPageUrl()
        assertTrue(url.contains("mod=spacecp"))
        assertTrue(url.contains("ac=album"))
    }
}
