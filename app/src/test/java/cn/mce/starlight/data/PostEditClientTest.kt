package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.4.6：原生编辑表单解析/提交 body 测试 */
class PostEditClientTest {

    private fun editPage(subject: String = "测试主题", message: String = "正文 [img]a.png[/img]"): String =
        """
        <html><body><form method="post" action="forum.php?mod=post&action=edit">
        <input type="hidden" name="formhash" value="abc123" />
        <input type="hidden" name="posttime" value="1700000000" />
        <input type="text" name="subject" value="$subject" />
        <textarea name="message" rows="10">$message</textarea>
        <input type="submit" name="editsubmit" value="保存" />
        </form></body></html>
        """.trimIndent()

    @Test
    fun parse_normalForm() {
        val f = PostEditClient.parse(editPage())
        assertNotNull(f)
        assertEquals("abc123", f!!.formhash)
        assertEquals("1700000000", f.posttime)
        assertEquals("测试主题", f.subject)
        assertTrue(f.message.contains("[img]"))
    }

    @Test
    fun parse_blankHtml_returnsNull() {
        assertNull(PostEditClient.parse(""))
    }

    @Test
    fun parse_missingFormhash_returnsNull() {
        assertNull(PostEditClient.parse("<html><textarea name=\"message\">x</textarea></html>"))
    }

    @Test
    fun buildBody_containsAllFieldsEncoded() {
        val f = PostEditClient.EditForm("hash1", "123", "标题", "正文")
        val body = PostEditClient.buildBody(f, "新标题", "新正文")
        assertTrue(body.contains("formhash=hash1"))
        assertTrue(body.contains("posttime=123"))
        assertTrue(body.contains("editsubmit=yes"))
        assertFalse(body.contains(" "))
    }

    @Test
    fun buildBody_chineseEncoded() {
        val f = PostEditClient.EditForm("h", "", "标题", "正文")
        val body = PostEditClient.buildBody(f, "标题", "正文 [b]加粗[/b]")
        assertTrue(body.contains("%E6%A0%87%E9%A2%98"))
    }
}
