package cn.mce.starlight.data

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

    /** 兼容旧调用：UA 统一由 [Net] 维护 */
    const val MOBILE_UA = Net.MOBILE_UA

    /** 桌面版 UA（强制电脑版模板时用） */
    const val DESKTOP_UA = Net.DESKTOP_UA

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

    /**
     * 提交表单（GET 拼参数 / POST 表单编码），返回响应体；失败返回 null。
     *
     * v0.3.3 批 4：以前只要 HTTP 200 就 `true`，签到/任务等表单的失败提示
     * （需要验证码 / 已签到 / 无权操作）也被当成成功。现在把响应交给调用方，
     * 用 [DiscuzResponse] 判定。
     */
    suspend fun submit(form: PageBlock.Form): String? = withContext(Dispatchers.IO) {
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
                get(
                    if (form.action.contains("?")) "${form.action}&$query" else "${form.action}?$query",
                    retryOnFailure = false,
                )
            }
        }.getOrNull()
    }

    private fun isInternal(href: String): Boolean =
        !href.startsWith("http") || href.contains("mce.msi.cn")

    private fun absolutize(href: String): String = Net.absolutize(href)

    fun get(
        url: String,
        cookieOverride: String? = null,
        userAgent: String? = null,
        /** 不把本次响应的 Set-Cookie 写入 CookieJar（用于"强制电脑版模板"这类一次性探路请求） */
        isolateCookies: Boolean = false,
        /**
         * v0.3.9 弱网：GET 失败（IO/超时）是否自动重试 1 次。
         * 默认 true（读页面是幂等的）；表单经 GET 提交（submit）要传 false，避免重复提交。
         */
        retryOnFailure: Boolean = true,
    ): String {
        fun once() = follow(
            url,
            "GET",
            cookieOverride ?: WebCookies.cookieHeader(url),
            null,
            userAgent,
            isolateCookies,
        )
        if (!retryOnFailure) return once()
        return try {
            once()
        } catch (e: IOException) {
            // 指数退避 600ms 后重试一次；仍失败则抛「网络较弱」友好文案（保留原始 cause 便于诊断）
            runCatching { Thread.sleep(Net.RETRY_BACKOFF_MS) }
            try {
                once()
            } catch (e2: IOException) {
                throw IOException(Net.WEAK_NETWORK_MESSAGE, e2)
            }
        }
    }

    /**
     * 手动跟随 3xx：每跳都保存 Set-Cookie。
     * Discuz 登录/回帖的关键 Cookie 往往在 302 响应里，用自动跟随会全部丢失，
     * 表现为"登录了却一直是游客"（历史疑难 bug）。
     */
    private fun follow(
        startUrl: String,
        method: String,
        cookie: String?,
        body: String?,
        userAgent: String? = null,
        isolateCookies: Boolean = false,
    ): String {
        var currentUrl = startUrl
        var currentCookie = cookie
        var currentMethod = method
        var currentBody = body
        var hops = 0
        val ua = userAgent ?: MOBILE_UA

        while (true) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = Net.CONNECT_TIMEOUT_MS
                readTimeout = Net.READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = currentMethod
                // v0.4.1：论坛/代理/CDN 会缓存页面，导致「网页有新帖、APP 刷新看不到」——强制不走缓存
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", ua)
                currentCookie?.let { setRequestProperty("Cookie", it) }
                if (currentMethod == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            try {
                if (currentMethod == "POST" && currentBody != null) {
                    conn.outputStream.use { it.write(currentBody.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode

                // 逐跳保存 Cookie（这是关键）；一次性探路请求（强制电脑版模板）不落盘
                if (!isolateCookies) storeCookies(currentUrl, conn)

                if (code in 300..399 && hops < 6) {
                    val location = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    currentUrl = URL(URL(currentUrl), location).toString()
                    currentCookie = WebCookies.cookieHeader(currentUrl) ?: currentCookie
                    currentMethod = "GET"
                    currentBody = null
                    hops++
                    continue
                }

                if (code !in 200..299) throw IOException("HTTP $code")
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        throw IOException("too many redirects")
    }

    fun httpPost(
        url: String,
        body: String,
        cookie: String?,
        userAgent: String? = null,
    ): String =
        follow(url, "POST", cookie ?: WebCookies.cookieHeader(url), body, userAgent)
    /** 保存响应 Cookie：只写入自建 CookieJar（v0.3.3 批 2 已删除 CookieManager 镜像/flush） */
    private fun storeCookies(url: String, conn: HttpURLConnection) {
        runCatching {
            val headers = conn.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values.flatten()
            if (headers.isNotEmpty()) WebCookies.saveFromHeaders(headers)
        }
    }

    /** 是否被重定向到登录页（会话失效）；判定逻辑统一在 [Net] */
    fun isLoginPage(html: String): Boolean = Net.isLoginPage(html)

    /** 是否被论坛频率/安全策略拦截；判定逻辑统一在 [Net] */
    fun isBlockedPage(html: String): Boolean = Net.isBlockedPage(html)
}
