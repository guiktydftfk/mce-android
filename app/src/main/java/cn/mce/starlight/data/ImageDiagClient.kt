package cn.mce.starlight.data

import cn.mce.starlight.core.DebugDump
import cn.mce.starlight.core.WebCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

/**
 * 图片自检：抓取帖子页 → 解析图片 → 逐个请求探测，输出失败原因。
 * 用于定位「某些帖子图片看不到」的通病。
 */
object ImageDiagClient {
    private const val BASE = Net.BASE
    // v0.3.3 批 3：正则常量
    private val whitespaceRegex = Regex("""\s+""")
    const val MOBILE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

    data class Probe(
        val url: String,
        val code: Int,
        val contentType: String?,
        val length: Long,
        val looksHtml: Boolean,
        val preview: String,
    )

    data class Report(
        val threadUrl: String,
        val htmlLength: Int,
        val imgTags: Int,
        val parsedCount: Int,
        val probes: List<Probe>,
        val notes: List<String>,
    )

    suspend fun run(tid: Int): Report = withContext(Dispatchers.IO) {
        val threadUrl = "$BASE/forum.php?mod=viewthread&tid=$tid"
        val html = GenericPageClient.get(threadUrl)
        DebugDump.dump("thread_$tid", html)

        val doc = Jsoup.parse(html, threadUrl)
        val imgTags = doc.select("img").size

        // 与正式解析保持同一套规则
        val posts = ThreadDetailParser.parse(html, threadUrl, tid, 1)
        val urls = posts.posts.flatMap { it.images }.distinct()

        val notes = mutableListOf<String>()

        // 逐张图片的判定过程（定位"为什么被过滤"）
        doc.select("img").forEachIndexed { index, img ->
            val inMessage = img.parents().any { it.hasClass("message") }
            val raw = listOf("zoomfile", "file", "data-src", "data-original", "src")
                .associateWith { img.attr(it) }
                .filterValues { it.isNotBlank() }
            notes += "IMG[$index] inMessage=$inMessage attrs=$raw"
        }
        if (imgTags == 0) notes += "页面里没有 <img> 标签（可能是登录失效/无权限页）"
        if (urls.isEmpty() && imgTags > 0) notes += "有 <img> 但解析结果为空（筛选规则把它们排除了）"

        notes += "posts=${posts.posts.size}"
        posts.posts.forEachIndexed { i, p ->
            notes += "POST[$i] pid=${p.pid} blocks=${p.blocks.size} imgs=${p.images.size} textLen=${p.contentText.length}"
        }
        val probes = urls.take(20).map { url -> probe(url) }

        val report = Report(threadUrl, html.length, imgTags, urls.size, probes, notes)
        DebugDump.dump(
            "image_report_$tid",
            buildString {
                appendLine("thread=$threadUrl html=${html.length} img=$imgTags parsed=${urls.size}")
                notes.forEach { appendLine("note=$it") }
                probes.forEach { p ->
                    appendLine("[${p.code}] type=${p.contentType} len=${p.length} html=${p.looksHtml}")
                    appendLine("  url=${p.url}")
                    appendLine("  head=${p.preview}")
                }
            },
        )
        report
    }

    private fun probe(url: String): Probe {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", MOBILE_UA)
                setRequestProperty("Referer", "$BASE/forum.php")
                WebCookies.cookieHeader(url)?.let { setRequestProperty("Cookie", it) }
            }
            val code = conn.responseCode
            val type = conn.contentType
            val len = conn.contentLengthLong
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { input ->
                val buffer = ByteArray(600)
                val read = input.read(buffer)
                if (read > 0) buffer.copyOf(read) else ByteArray(0)
            } ?: ByteArray(0)
            val head = String(bytes, Charsets.UTF_8)
            val looksHtml = head.trimStart().startsWith("<")
            Probe(
                url = url,
                code = code,
                contentType = type,
                length = len,
                looksHtml = looksHtml,
                preview = head.replace(whitespaceRegex, " ").take(120),
            )
        } catch (e: Exception) {
            Probe(url, -1, null, 0, false, "异常: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }
}
