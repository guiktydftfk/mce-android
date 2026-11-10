package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v0.4.6：列表卡片回复数解析 —— 不能把「阅读数」当回复数 */
class ReplyCountParseTest {

    private fun page(metaText: String): String =
        """
        <html><body><ul>
          <li class="subjects_1 mtb"><span class="y">$metaText</span>
            <a class="th_item" href="forum.php?mod=viewthread&tid=100"><h1>测试帖子</h1></a>
          </li>
        </ul></body></html>
        """.trimIndent()

    private fun repliesOf(metaText: String): Int? =
        MobileForumParser.parse(page(metaText), "https://mce.msi.cn/", 1).threads.firstOrNull()?.replies

    @Test
    fun onlyViews_noReplies_isNull() {
        // 「昨天 15:51 / 10 阅读」：未开放回复，回复数必须为空而不是 10
        assertNull(repliesOf("昨天 15:51 / 10 阅读"))
    }

    @Test
    fun repliesAfterCount_parsed() {
        assertEquals(5, repliesOf("昨天 15:51 / 5 回复"))
    }

    @Test
    fun repliesBeforeCount_parsed() {
        assertEquals(7, repliesOf("回复 7"))
    }

    @Test
    fun bothViewsAndReplies_usesReplies() {
        assertEquals(3, repliesOf("昨天 15:51 / 3 回复 / 10 阅读"))
    }
}
