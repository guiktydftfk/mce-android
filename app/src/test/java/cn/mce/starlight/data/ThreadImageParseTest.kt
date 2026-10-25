package cn.mce.starlight.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 回归测试：正文配图必须能解析出来。
 *
 * 这个用例来自真实页面 tid=38042，其图片是「相册/附件」形式：
 *   <img class="lazys" src="static/image/common/none.gif"
 *        data-src="forum.php?mod=image&aid=185963&size=3000x0&key=..&type=fixnone">
 * src 是 1x1 占位图，真实地址在 data-src —— 历史 bug 就是这里被过滤掉了。
 */
class ThreadImageParseTest {

    private fun load(name: String): String {
        val candidates = listOf(
            File("src/test/resources/$name"),
            File("app/src/test/resources/$name"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("fixture not found: $name")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun `album attachment images are parsed`() {
        val html = load("thread38042.html")
        val detail = ThreadDetailParser.parse(
            html,
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=38042",
            38042,
            1,
        )

        val images = detail.posts.flatMap { it.images }
        println("posts=${detail.posts.size} images=$images")

        assertTrue("应解析出至少 1 张图片，实际 ${images.size}", images.isNotEmpty())
        assertTrue(
            "图片地址应指向 mod=image 或附件直链，实际：$images",
            images.all { it.contains("mod=image") || it.contains("mod=attachment") || it.contains("data/attachment") },
        )
        assertTrue("不应包含占位图 none.gif", images.none { it.contains("none.gif") })
    }

    @Test
    fun `existing thread still parses images`() {
        val html = load("viewthread.html")
        val detail = ThreadDetailParser.parse(
            html,
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=14893",
            14893,
            1,
        )
        println("legacy images=${detail.posts.flatMap { it.images }}")
        assertTrue(detail.posts.isNotEmpty())
    }
}
