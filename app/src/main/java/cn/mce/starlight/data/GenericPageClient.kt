package cn.mce.starlight.data

import android.webkit.CookieManager
import cn.mce.starlight.core.WebCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 通用页面内容块：把 Discuz 页面拆成原生可绘制的元素 */
sealed interface PageBlock {
    data class Heading(val text: String) : PageBlock
    data class Paragraph(val text: String) : PageBlock
    data class Link(val text: String, val url: String, val internal: Boolean) : PageBlock
    data class Image(val url: String) : PageBlock
    data class Form(
        val action: String,
        val method: String,
        val fields: List<Pair<String, String>>,
        val submitLabel: String,
    ) : PageBlock
}

data class GenericPage(
    val url: String,
    val title: String,
    val blocks: List<PageBlock>,
    val requiresLogin: Boolean,
)

/**
 * 通用页面抓取 + 解析。
 * 用于签到、任务中心、搜索等「表单类」页面，避免退回 WebView。
 */
object GenericPageClient {

    const val MOBILE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 " +
            "Mobile/15E148 Safari/604.1"

    suspend fun load(url: String): GenericPage = withContext(Dispatchers.IO) {
        val html = get(url)
        val doc = Jsoup.parse(html, url)

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().substringBefore(" - ").trim()

        val requiresLogin = doc.selectFirst("input[name=formhash]") != null &&
            doc.selectFirst("input[name=password]") != null &&
            doc.selectFirst("a[href*=action=logout]") == null

        val root = doc.selectFirst("div.content")
            ?: doc.selectFirst("div.mm-page")
            ?: doc.body()

        val blocks = buildList {
            // 表单（最多一个：签到 / 任务申请）
            root.selectFirst("form")?.let { form ->
                val action = form.absUrl("action").ifBlank { url }
                val method = form.attr("method").ifBlank { "get" }
                val fields = form.select("input[name]")
                    .filter { it.attr("type").lowercase() != "submit" }
                    .map { it.attr("name") to it.attr("value") }
                val submitLabel = form.selectFirst("button, input[type=submit], .btn, .y")
                    ?.text()?.trim()?.takeIf { it.isNotBlank() } ?: "提交"
                if (fields.isNotEmpty()) {
                    add(
                        PageBlock.Form(
                            action = action,
                            method = method,
                            fields = fields,
                            submitLabel = submitLabel,
                        ),
                    )
                }
            }

            // 标题与段落
            root.select("h1, h2, h3").forEach { el ->
                val t = el.text().trim()
                if (t.isNotBlank()) add(PageBlock.Heading(t))
            }

            root.select("p").forEach { el ->
                val t = el.text().trim()
                if (t.isNotBlank() && t.length < 500) add(PageBlock.Paragraph(t))
            }

            // 图片
            root.select("img[src]").take(6).forEach { img ->
                val src = img.attr("src").takeIf { it.isNotBlank() } ?: return@forEach
                add(PageBlock.Image(absolutize(src)))
            }

            // 主要链接（过滤导航噪音）
            root.select("a[href]").forEach { a ->
                val text = a.text().trim()
                val href = a.attr("href")
                if (text.length in 2..30 &&
                    !href.startsWith("javascript") &&
                    !href.contains("action=logout") &&
                    !href.contains("action=login") &&
                    !text.contains("登录") &&
                    !text.contains("注册")
                ) {
                    add(PageBlock.Link(text, absolutize(href), isInternal(href)))
                }
            }
        }.distinctBy { it.toString() }

        GenericPage(url = url, title = title, blocks = blocks.take(80), requiresLogin = requiresLogin)
    }

    /** 提交表单（GET 拼参数 / POST 表单编码），返回是否成功 */
    suspend fun submit(form: PageBlock.Form): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cookie = WebCookies.cookieHeader()
            if (form.method.equals("post", true)) {
                val body = form.fields
                    .filter { it.first !in setOf("formhash") || it.second.isNotBlank() }
                    .joinToString("&") {
                        URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
                    }
                httpPost(form.action, body, cookie)
            } else {
                val query = form.fields
                    .filter { it.second.isNotBlank() }
                    .joinToString("&") {
                        URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
                    }
                get(if (form.action.contains("?")) "${form.action}&$query" else "${form.action}?$query")
            }
            true
        }.getOrDefault(false)
    }

    private fun isInternal(href: String): Boolean =
        !href.startsWith("http") || href.contains("mce.msi.cn")

    private fun absolutize(href: String): String = when {
        href.startsWith("//") -> "https:$href"
        href.startsWith("http") -> href
        href.startsWith("/") -> "https://mce.msi.cn$href"
        else -> "https://mce.msi.cn/$href"
    }

    fun get(url: String, cookieOverride: String? = null): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", MOBILE_UA)
            (cookieOverride ?: WebCookies.cookieHeader(url))?.let {
                setRequestProperty("Cookie", it)
            }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            storeCookies(url, conn)
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun httpPost(url: String, body: String, cookie: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            cookie?.let { setRequestProperty("Cookie", it) }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            storeCookies(url, conn)
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    /** 保存响应 Cookie：写入自建 CookieJar（不依赖 WebView），并尽力兼容 CookieManager */
    private fun storeCookies(url: String, conn: HttpURLConnection) {
        runCatching {
            val headers = conn.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values.flatten()
            if (headers.isNotEmpty()) WebCookies.saveFromHeaders(headers)
        }
    }

    fun document(url: String): Document = Jsoup.parse(get(url), url)
}
