package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：需要密码的帖子。
 *
 * 论坛对需要密码的帖子返回的是「只含密码表单」的页面（没有正文楼层），
 * 旧实现会解析出 0 楼、界面空白；现在解析成 [ThreadDetail.passwordRequired]
 * 并携带 [PageBlock.Form]，由详情页弹窗输入密码后提交解锁。
 */
class PasswordThreadParseTest {

    private val passwordHtml = """
        <html><head><title>需要密码的帖子 - 综合交流 - MCE星光班</title></head>
        <body>
          <div class="thname"><a href="forum.php?mod=viewthread&amp;tid=42">需要密码的帖子</a></div>
          <div class="pwform">
            <form action="forum.php?mod=viewthread&amp;tid=42&amp;mobile=2" method="post">
              <input type="hidden" name="formhash" value="ab12cd34">
              <input type="hidden" name="referer" value="forum.php?mod=viewthread&amp;tid=42">
              <input type="password" name="password" value="">
              <button type="submit" name="pwsubmit" value="yes">提交</button>
            </form>
          </div>
        </body></html>
    """.trimIndent()

    private fun parse(html: String): ThreadDetail = ThreadDetailParser.parse(
        html,
        "https://mce.msi.cn/forum.php?mod=viewthread&tid=42&mobile=2",
        42,
        1,
    )

    @Test
    fun passwordPage_isFlaggedAsPasswordRequired() {
        val detail = parse(passwordHtml)
        assertTrue("应判定为需要密码", detail.passwordRequired)
        assertTrue("密码页没有正文楼层", detail.posts.isEmpty())
        assertEquals(42, detail.tid)
    }

    @Test
    fun passwordPage_carriesFormWithHiddenFields() {
        val form = parse(passwordHtml).passwordForm
        assertNotNull("应解析出密码表单", form)
        assertTrue("表单 action 应为绝对地址", form!!.action.startsWith("https://mce.msi.cn/"))
        assertTrue("表单 method 应为 post", form.method.equals("post", ignoreCase = true))
        assertTrue(
            "应带 formhash 隐藏字段",
            form.fields.any { it.first == "formhash" && it.second == "ab12cd34" },
        )
        assertTrue(
            "应带 password 字段（值可为空，提交时填）",
            form.fields.any { it.first == "password" },
        )
    }

    @Test
    fun normalThread_isNotPasswordRequired() {
        val normal = """
            <html><head><title>普通帖子 - 综合交流</title></head>
            <body>
              <div class="thname"><a href="#">普通帖子</a></div>
              <div class="plc" id="pid700001">
                <div class="message"><p>正常正文内容</p></div>
              </div>
            </body></html>
        """.trimIndent()
        val detail = parse(normal)
        assertFalse("普通帖子不应被判定为需要密码", detail.passwordRequired)
        assertEquals(1, detail.posts.size)
    }
}
