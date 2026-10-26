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

    /**
     * v0.3.3（批 1）新接口：列表缩略图用的轻量首图提取。
     * 不应该为了拿一个 URL 就跑完整楼层解析。
     */
    @Test
    fun `firstImageUrl returns the same first image cheaply`() {
        val html = load("thread38042.html")
        val base = "https://mce.msi.cn/forum.php?mod=viewthread&tid=38042"
        val full = ThreadDetailParser.parse(html, base, 38042, 1)
            .posts.flatMap { it.images }.firstOrNull()
        val fast = ThreadDetailParser.firstImageUrl(html, base)
        println("full.first=$full fast=$fast")
        assertTrue("全量解析应能取到图片", !full.isNullOrBlank())
        assertTrue("轻量提取应与全量解析的首图一致", fast == full)
    }

    @Test
    fun `firstImageUrl ignores avatars and placeholders`() {
        val html = """
            <div class="plc" id="pid1">
              <div class="pi"><img src="uc_server/avatar.php?uid=2&size=middle" /></div>
              <div class="message">
                <img src="static/image/common/none.gif" data-src="static/image/common/none.gif" />
                只有文字，没有配图
              </div>
            </div>
        """.trimIndent()
        val url = ThreadDetailParser.firstImageUrl(html, "https://mce.msi.cn/forum.php?mod=viewthread&tid=1")
        println("avatar/placeholder only -> $url")
        assertTrue("头像/占位图不应作为缩略图，实际：$url", url == null)
    }
}
