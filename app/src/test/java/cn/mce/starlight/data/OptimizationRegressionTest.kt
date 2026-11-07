package cn.mce.starlight.data

import cn.mce.starlight.core.PmReadStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.4.4：为最近修复的关键逻辑补回归测试（优化批次顺手补齐）。
 * - PmReadStore 已读签名容错（列表 preview 截断也能匹配，新消息不能误判已读）
 * - UserThreadsClient 只解析主题表格（不误收导航链接）
 * - 评分区块解析
 */
class OptimizationRegressionTest {

    // ---------- 私信已读匹配 ----------

    @Test
    fun pmRead_exactAndWhitespace() {
        assertTrue(PmReadStore.matches("你好 世界", "  你好   世界 "))
    }

    @Test
    fun pmRead_truncatedPreviewMatches() {
        val full = "诶嘿，今天的活动安排是下午三点集合，记得带水杯和外套"
        assertTrue(PmReadStore.matches(full, "诶嘿，今天的活动安排是下午三点..."))
        assertTrue(PmReadStore.matches("诶嘿，今天的活动安排是下午三点…", full))
    }

    @Test
    fun pmRead_newMessageNotTreatedAsRead() {
        // 读过「好的」，之后来了新消息「好的，我马上到」——不能被误判为已读
        assertFalse(PmReadStore.matches("好的", "好的，我马上到"))
        assertFalse(PmReadStore.matches(null, "任意内容"))
        assertFalse(PmReadStore.matches("", "任意内容"))
    }

    // ---------- TA 的主题：只解析主题表格 ----------

    @Test
    fun userThreads_parsesTableAndSkipsNavLinks() {
        val html = """
            <html><body>
            <div class="tl"><table>
              <tr class="th"><td class="icn">&nbsp;</td><th>主题</th><td class="frm">版块</td>
                  <td class="num">回复/查看</td><td class="by"><cite>最后发帖</cite></td></tr>
              <tr>
                <td class="icn"><a href="forum.php?mod=viewthread&tid=37657&amp;highlight="></a></td>
                <th><a href="forum.php?mod=viewthread&amp;tid=37657">牛师傅上门装机记</a></th>
                <td><a href="forum.php?mod=forumdisplay&amp;fid=2" class="xg1">综合交流</a></td>
                <td class="num"><a class="xi2">4</a><em>33</em></td>
                <td class="by"><cite><a href="home.php?mod=space&amp;username=x">肉丸鸭鸭</a></cite>
                    <em><a>2026-10-19 20:41</a></em></td>
              </tr>
              <tr>
                <th><a href="forum.php?mod=viewthread&amp;tid=37445">近期的喜剧之王大家都看了吗</a></th>
                <td><a href="forum.php?mod=forumdisplay&amp;fid=2" class="xg1">综合交流</a></td>
                <td class="num"><a class="xi2">12</a><em>51</em></td>
                <td class="by"><cite><a>Brian</a></cite><em><a>2026-10-11 15:11</a></em></td>
              </tr>
            </table></div>
            <a href="forum.php?mod=viewthread&tid=11219">常用表单</a>
            </body></html>
        """.trimIndent()
        val items = UserThreadsClient.parse(html)
        assertEquals(2, items.size)
        assertEquals(listOf(37657, 37445), items.map { it.tid })
        assertEquals("综合交流", items[0].board)
        assertEquals(4, items[0].replies)
        assertEquals(33, items[0].views)
        assertEquals("肉丸鸭鸭", items[0].lastPoster)
        // 表格外的「常用表单」导航链接不应被收录
        assertFalse(items.any { it.title == "常用表单" })
    }

    // ---------- 评分区块 ----------

    @Test
    fun ratings_parsesEntriesAndTotals() {
        val html = """
            <html><body>
            <dl class="rate">
              <dd><table class="ratl">
                <tr><th><a href="forum.php?mod=misc&amp;action=viewratings&amp;tid=1">
                    参与人数 <span class="xi1">2</span></a>
                    赞赏值 <i><span class="xi1">+4</span></i></th></tr>
                <tbody class="ratl_l">
                  <tr>
                    <td class="xi1">+2</td>
                    <td class="xg1">很棒</td>
                    <td><a href="home.php?mod=space&amp;uid=2782">田田园</a>
                        <img class="user_avatar" src="uc_server/avatar.php?uid=2782"></td>
                  </tr>
                  <tr>
                    <td class="xi1">+2</td>
                    <td class="xg1"></td>
                    <td><a href="home.php?mod=space&amp;uid=3001">Brian</a></td>
                  </tr>
                </tbody>
              </table></dd>
            </dl>
            </body></html>
        """.trimIndent()
        val ratings = ThreadDetailParser.parseRatingsFromHtml(html, Net.BASE)
        assertNotNull(ratings)
        val r = ratings!!
        assertEquals(2, r.count)
        assertEquals(2, r.entries.size)
        assertEquals("田田园", r.entries[0].name)
        assertEquals("+2", r.entries[0].amount)
        assertEquals("很棒", r.entries[0].reason)
    }
}
