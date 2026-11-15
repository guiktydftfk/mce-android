package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.8：隐藏内容解析（回复可见 / 登录可见 / 积分购买可见 / 需要密码）。
 *
 * 论坛手机模板对无权限用户只返回一句提示文字，旧实现会把它当普通正文，
 * 用户看到的是「回复后可见」这种孤立句子甚至空白；现在解析成
 * [ContentBlock.Hidden]，由详情页渲染成醒目提示卡片。
 */
class HiddenContentParseTest {

    private fun parseFirstPost(messageHtml: String): Post {
        val html = """
            <html><body>
              <div class="thname"><a href="#">隐藏内容测试帖</a></div>
              <div class="plc" id="pid910001">
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
    fun showhideContainer_becomesHiddenReply() {
        val post = parseFirstPost(
            """
            <p>正文前情提要</p>
            <div class="showhide">
              <div class="showhide_title">此处内容需要回复后才能查看</div>
            </div>
            """.trimIndent(),
        )
        val hidden = post.blocks.filterIsInstance<ContentBlock.Hidden>().firstOrNull()
        assertNotNull("应解析出 ContentBlock.Hidden", hidden)
        assertEquals(HiddenKind.Reply, hidden!!.kind)
        assertTrue(hidden.text.contains("回复"))
    }

    @Test
    fun lockedContainer_becomesHiddenLogin() {
        val post = parseFirstPost(
            """
            <div class="locked">您需要登录后才可以查看本帖内容</div>
            """.trimIndent(),
        )
        val hidden = post.blocks.filterIsInstance<ContentBlock.Hidden>().firstOrNull()
        assertNotNull(hidden)
        assertEquals(HiddenKind.Login, hidden!!.kind)
    }

    @Test
    fun plainTextNotice_becomesHiddenPoints() {
        val post = parseFirstPost(
            """
            <p>以下内容需要积分购买后可见，感谢支持</p>
            """.trimIndent(),
        )
        val hidden = post.blocks.filterIsInstance<ContentBlock.Hidden>().firstOrNull()
        assertNotNull(hidden)
        assertEquals(HiddenKind.Points, hidden!!.kind)
    }

    @Test
    fun normalTextWithVisibleWord_isNotHidden() {
        // 「回复可见性分析」是正常讨论文字，不能误判成隐藏提示
        val post = parseFirstPost(
            """
            <p>大家对这个回复可见性分析怎么看</p>
            """.trimIndent(),
        )
        assertTrue(
            "普通段落不能被误判为隐藏内容",
            post.blocks.filterIsInstance<ContentBlock.Hidden>().isEmpty(),
        )
        assertTrue(
            "普通段落仍应按文字渲染",
            post.blocks.filterIsInstance<ContentBlock.Text>().any { it.text.contains("回复可见性分析") },
        )
    }

    @Test
    fun hiddenKindOf_defaultsToOtherForGenericNotice() {
        assertEquals(HiddenKind.Login, ThreadDetailParser.hiddenKindOf("请登录后查看"))
        assertEquals(HiddenKind.Reply, ThreadDetailParser.hiddenKindOf("回复后才可见"))
        assertEquals(HiddenKind.Points, ThreadDetailParser.hiddenKindOf("购买后可见"))
    }
}
