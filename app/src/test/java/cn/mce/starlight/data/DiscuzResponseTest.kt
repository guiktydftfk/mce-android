package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.3 批 4 回归：Discuz 响应判定。
 *
 * 样本来自真实抓包（模拟器 curl 实测）：
 *  - 回复成功页："非常感谢，回复发布成功，现在将转入主题页"
 *  - 回复被频控："抱歉，您两次发表间隔少于 15 秒，请稍候再发表"
 *  - 资料保存成功（AJAX）：`<script>parent.show_success('');</script>`
 *  - 评分自己帖子：`showError('抱歉，您不能给自己发表的帖子评分')`
 *
 * 旧逻辑：回复只判 `!contains("登录后") && !contains("请输入内容")` → 频控失败也会提示成功；
 * 评分只认 "error"/"抱歉"；资料保存只判 `!contains("超时") && !contains("错误")` → 恒成功。
 */
class DiscuzResponseTest {

    @Test
    fun `reply success page is success`() {
        val html = """
            <div class="tip"><dt id="messagetext">
            <p class="message_float">非常感谢，回复发布成功，现在将转入主题页，请稍候……</p>
            </dt></div>
        """.trimIndent()
        val (ok, msg) = DiscuzResponse.judge(html, "回复成功")
        assertTrue("回复成功页应判成功", ok)
        assertEquals("回复成功", msg)
    }

    @Test
    fun `flood control reply is failure with real message`() {
        val html = """
            <div class="tip"><dt id="messagetext">
            <p class="message_float">抱歉，您两次发表间隔少于 15 秒，请稍候再发表</p>
            </dt></div>
        """.trimIndent()
        val (ok, msg) = DiscuzResponse.judge(html, "回复成功")
        assertFalse("频控提示不能判成功", ok)
        println("flood msg = $msg")
        assertTrue("应带出服务端原文，实际：$msg", msg.contains("间隔少于"))
    }

    @Test
    fun `thread publish success page is success`() {
        val html = """
            <div class="tip"><dt id="messagetext">
            <p class="message_float">非常感谢，您的主题已发布，现在将转入主题页，请稍候……</p>
            </dt></div>
        """.trimIndent()
        assertTrue(DiscuzResponse.judge(html, "发布成功").first)
    }

    @Test
    fun `ajax show_success is success`() {
        val html = """<script type="text/javascript">parent.show_success('');</script>"""
        assertTrue(DiscuzResponse.isSuccess(html))
        assertTrue(DiscuzResponse.judge(html, "已保存").first)
    }

    @Test
    fun `ajax showError is failure`() {
        val html = """<script type="text/javascript">showError('抱歉，您不能给自己发表的帖子评分');</script>"""
        val (ok, msg) = DiscuzResponse.judge(html, "评分成功")
        assertFalse(ok)
        println("showError msg = $msg")
        assertTrue("应带出 showError 原文，实际：$msg", msg.contains("不能给自己"))
    }

    @Test
    fun `favorite success and duplicate favorite`() {
        val success = """<div class="alert_info">收藏成功</div>"""
        assertTrue(DiscuzResponse.judge(success, "已收藏").first)

        val duplicate = """<div class="alert_error">您已经收藏过该主题</div>"""
        val (ok, msg) = DiscuzResponse.judge(duplicate, "已收藏")
        assertFalse("重复收藏应判失败", ok)
        println("duplicate favorite msg = $msg")
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `unknown response is not treated as success`() {
        val html = "<html><body><div>某个没有任何提示的页面</div></body></html>"
        val (ok, msg) = DiscuzResponse.judge(html, "回复成功")
        assertFalse("没有明确成功证据时不应默认成功", ok)
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `profile save failure alert is failure`() {
        val html = """<div class="alert_error">抱歉，您输入的QQ号格式不正确</div>"""
        val (ok, msg) = DiscuzResponse.judge(html, "已保存")
        assertFalse(ok)
        assertTrue("应带出失败原文，实际：$msg", msg.contains("QQ"))
    }

    /** 真实抓包：提交非法 QQ 时返回 `parent.show_error("yahoo", "")`（双引号 + 两参数 + 空消息） */
    @Test
    fun `ajax show_error double quotes field validation is failure`() {
        val html = """<script>parent.show_error("yahoo", "");</script>"""
        val (ok, msg) = DiscuzResponse.judge(html, "已保存")
        assertFalse("字段校验失败不能判成功", ok)
        println("field validation msg = $msg")
        assertTrue("应指出是哪个字段，实际：$msg", msg.contains("yahoo"))
    }

    @Test
    fun `ajax show_error with message double quotes extracts message`() {
        val html = """<script>parent.show_error("两次评分间隔过短");</script>"""
        val (ok, msg) = DiscuzResponse.judge(html, "评分成功")
        assertFalse(ok)
        println("show_error msg = $msg")
        assertTrue("应带出 show_error 原文，实际：$msg", msg.contains("间隔"))
    }
}
