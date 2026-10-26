package cn.mce.starlight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.3.2 回归：表情缺小红龙（reddragon）。
 *
 * 旧实现用正则 `[^"'\s<>\\]+` 扫页面里的表情地址，`red dragon (12).jpg`
 * 这类带空格的文件名会被截断成 `.../reddragon/red`（404）。
 * 现在直接解析论坛表情定义文件 `data/cache/common_smilies_var.js`，
 * 并保留完整文件名（URL 里空格转 %20）。
 */
class SmileyParseTest {

    private fun load(name: String): String {
        val candidates = listOf(
            File("src/test/resources/$name"),
            File("app/src/test/resources/$name"),
        )
        val file = candidates.firstOrNull { it.exists() } ?: error("fixture not found: $name")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun `live smiley definition keeps full red dragon file names`() {
        val js = load("smilies_var.js")
        val sets = SmileyClient.parseSets(js)
        println("sets = " + sets.map { "${it.name}(${it.smileys.size})" })

        assertEquals("应解析出全部 4 套表情：小红龙/小红龙2/经典/小表情", 4, sets.size)

        val red = sets.first { it.dir == "reddragon" }
        assertEquals("小红龙", red.name)
        assertEquals(24, red.smileys.size)
        assertTrue(
            "小红龙文件名必须完整（空格转 %20），实际：${red.smileys.first().url}",
            red.smileys[0].url.endsWith("/static/image/smiley/reddragon/red%20dragon%20(1).jpg"),
        )
        assertTrue(
            "全套 24 个小程序都应为 reddragon 目录下的完整文件名",
            red.smileys.all { it.url.contains("/static/image/smiley/reddragon/red%20dragon%20(") },
        )
        assertEquals("{:5_176:}", red.smileys[0].code)

        val red2 = sets.first { it.dir == "reddragon2" }
        assertEquals(24, red2.smileys.size)
        assertTrue(red2.smileys.all { it.url.contains("/static/image/smiley/reddragon2/lucky%20(") })
    }

    @Test
    fun `classic smileys with escaped quote are parsed`() {
        val js = load("smilies_var.js")
        val sets = SmileyClient.parseSets(js)
        val classic = sets.first { it.dir == "default" }
        assertEquals("经典", classic.name)
        assertEquals(":)", classic.smileys[0].code)
        assertTrue(
            "含转义引号的表情代码 :'( 也要正确解析",
            classic.smileys.any { it.code == ":'(" },
        )
        assertTrue(classic.smileys.all { it.url.startsWith("https://mce.msi.cn/static/image/smiley/default/") })
    }

    @Test
    fun `thread content renders red dragon smiley as image`() {
        val html = """
            <div class="plc" id="pid100">
              <div class="view_authi"><span class="view_author"><a href="home.php?mod=space&uid=1">测试</a></span></div>
              <div class="message">还有截胡环节<img src="static/image/smiley/reddragon/red dragon (12).jpg" smilieid="179" border="0" /></div>
            </div>
        """.trimIndent()
        val detail = ThreadDetailParser.parse(
            html,
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=1",
            1,
            1,
        )
        val images = detail.posts.flatMap { it.images }
        println("images = $images")
        assertTrue(
            "正文表情（含空格文件名）应解析成 %20 编码的图片地址，实际：$images",
            images.any { it.contains("/smiley/reddragon/red%20dragon%20(12).jpg") },
        )
    }
}
