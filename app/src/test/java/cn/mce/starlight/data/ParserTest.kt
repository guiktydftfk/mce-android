package cn.mce.starlight.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析器单元测试：用真实抓取的页面样本（app/src/test/resources）验证选择器。
 * 这些测试在 JVM 上跑，不需要模拟器/手机。
 */
class ParserTest {

    private fun load(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8)
            .readText()

    @Test
    fun forumDisplayParsesThreads() {
        val html = load("forumdisplay.html")
        val page = MobileForumParser.parse(
            html,
            "https://mce.msi.cn/forum.php?mod=forumdisplay&fid=53",
            1,
        )
        println("[forumdisplay] threads=${page.threads.size} hasNext=${page.hasNextPage}")
        page.threads.take(5).forEach {
            println("  #${it.id} | ${it.title} | ${it.author} | ${it.metaText} | ${it.typeLabel}")
        }
        assertTrue("should parse threads", page.threads.isNotEmpty())
        assertTrue("tid should be positive", page.threads.first().id > 0)
        assertTrue("title should be non-blank", page.threads.first().title.isNotBlank())
    }

    @Test
    fun guideParsesThreads() {
        val html = load("guide.html")
        val page = MobileForumParser.parse(
            html,
            "https://mce.msi.cn/forum.php?mod=guide&view=newthread",
            1,
        )
        println("[guide] threads=${page.threads.size}")
        page.threads.take(5).forEach {
            println("  #${it.id} | ${it.title} | ${it.author}")
        }
        assertTrue("guide should parse threads", page.threads.isNotEmpty())
    }

    @Test
    fun viewThreadParsesPosts() {
        val html = load("viewthread.html")
        val detail = ThreadDetailParser.parse(
            html,
            "https://mce.msi.cn/forum.php?mod=viewthread&tid=14893",
            14893,
            1,
        )
        println("[viewthread] title=${detail.title} replies=${detail.replies} posts=${detail.posts.size}")
        detail.posts.take(4).forEach {
            println(
                "  pid=${it.pid} author=${it.author} floor=${it.floorLabel} " +
                    "date=${it.dateText} textLen=${it.contentText.length} avatar=${it.avatarUrl}",
            )
        }
        assertTrue("should parse posts", detail.posts.isNotEmpty())
        assertTrue("first post content not empty", detail.posts.first().contentText.isNotBlank())
    }
}
