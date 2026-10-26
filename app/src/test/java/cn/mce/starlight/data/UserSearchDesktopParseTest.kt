package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.3.3 批 5：用户搜索的电脑版模板回退。
 *
 * 背景（真实抓包）：论坛手机模板缺 `mytpl/menubottom.htm`，用户搜索页
 * （`home.php?mod=spacecp&ac=search`）在手机模板下返回 503；加 `&mobile=no`
 * 强制电脑版模板后正常渲染，结果结构是 `ul.buddy > li`。
 */
class UserSearchDesktopParseTest {

    private fun load(name: String): String {
        val candidates = listOf(
            File("src/test/resources/$name"),
            File("app/src/test/resources/$name"),
        )
        val file = candidates.firstOrNull { it.exists() } ?: error("fixture not found: $name")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun `desktop user search results are parsed`() {
        val html = load("user_search_desktop.html")
        val users = SearchClient.parseDesktopUsers(html)
        println("desktop users = " + users.map { "${it.uid}:${it.name}" })
        assertTrue("应解析出至少 1 个用户，实际 ${users.size}", users.isNotEmpty())
        val brian = users.firstOrNull { it.name == "Brian" }
        assertTrue("应能解析到 Brian，实际 ${users.map { it.name }}", brian != null)
        assertEquals(2499, brian!!.uid)
        assertTrue("头像地址应解析，实际 ${brian.avatarUrl}", brian.avatarUrl.orEmpty().contains("avatar.php"))
    }

    @Test
    fun `desktop parse ignores pages without user list`() {
        val html = "<html><body><div>没有找到相关用户，换个搜索条件试试</div></body></html>"
        assertTrue(SearchClient.parseDesktopUsers(html).isEmpty())
    }
}
