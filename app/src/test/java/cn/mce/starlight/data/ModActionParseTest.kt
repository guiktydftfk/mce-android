package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.1：版主管理条（对齐网页端）。
 *
 * 网页端版主在帖子页顶部会看到一条管理操作栏：
 *  删除主题 / 升降 / 置顶 / 直播 / 高亮 / 精华 / 图章 / 关闭 / 移动 / 分类 / 复制 / 合并 / 分割 / 修复 / 警告 / 屏蔽 / 用户标签
 * 客户端把这些链接解析成 `ThreadDetail.modActions`，在帖子页渲染成「版主操作」横条，点开用原生页打开。
 */
class ModActionParseTest {

    private fun parse(messageHtml: String): ThreadDetail {
        val html = """
            <html><body>
              <div class="thname"><a href="#">版主操作测试帖</a></div>
              <div class="plc" id="pid900001">
                <div class="message">$messageHtml</div>
              </div>
            </body></html>
        """.trimIndent()
        return ThreadDetailParser.parse(
            html,
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=1",
            1,
            1,
        )
    }

    private fun link(action: String, label: String): String =
        """<a href="forum.php?mod=topicadmin&amp;action=$action&amp;tid=1&amp;formhash=abc">$label</a>"""

    @Test
    fun modBar_allWebActionsAreParsed() {
        val actions = listOf(
            "delete" to "删除主题",
            "updown" to "升降",
            "stick" to "置顶",
            "live" to "直播",
            "highlight" to "高亮",
            "digest" to "精华",
            "stamp" to "图章",
            "close" to "关闭",
            "move" to "移动",
            "type" to "分类",
            "copy" to "复制",
            "merge" to "合并",
            "split" to "分割",
            "repair" to "修复",
            "warn" to "警告",
            "ban" to "屏蔽",
            "usertag" to "用户标签",
        )
        val detail = parse(actions.joinToString("\n") { (action, label) -> link(action, label) })
        val labels = detail.modActions.map { it.label }
        assertEquals(actions.map { it.second }, labels)
        assertTrue(detail.modActions.all { it.url.startsWith("https://mce.msi.cn/forum.php?") })
        assertTrue(detail.modActions.all { it.url.contains("topicadmin") })
    }

    @Test
    fun modBar_absentForNormalUser() {
        // 普通用户页面没有 topicadmin 链接：不能出现管理入口
        val detail = parse("<p>普通回复内容</p>")
        assertTrue(detail.modActions.isEmpty())
    }

    @Test
    fun modBar_ignoresNonModLinks() {
        // 普通「编辑自己的帖子」链接（mod=post&action=edit）不算管理操作
        val detail = parse(
            """
            <a href="forum.php?mod=post&amp;action=edit&amp;tid=1">编辑</a>
            <a href="forum.php?mod=topicadmin&amp;action=delete&amp;tid=1">删除主题</a>
            """.trimIndent(),
        )
        assertEquals(listOf("删除主题"), detail.modActions.map { it.label })
    }
}
