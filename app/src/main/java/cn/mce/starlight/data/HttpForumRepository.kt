package cn.mce.starlight.data

import cn.mce.starlight.core.WebCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 数据仓库实现：HttpURLConnection + Jsoup。
 * 请求统一携带：
 *  - 移动 UA（让 Discuz 返回触屏模板）
 *  - WebView 的 Cookie（登录态自然共享）
 */
class HttpForumRepository(
    private val baseHost: String = "https://mce.msi.cn",
) : ForumRepository {

    companion object {
        const val MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 " +
                "Mobile/15E148 Safari/604.1"
    }

    override suspend fun loadBoards(): List<ForumBoard> = Boards.all

    override suspend fun loadThreads(fid: Int, page: Int, filter: String?): ForumPage =
        withContext(Dispatchers.IO) {
            val extra = when (filter) {
                "lastpost" -> "&filter=lastpost&orderby=lastpost"
                "heat" -> "&filter=heat&orderby=heats"
                "digest" -> "&filter=digest&digest=1"
                else -> ""
            }
            val url = "$baseHost/forum.php?mod=forumdisplay&fid=$fid&page=$page$extra"
            MobileForumParser.parse(fetch(url), url, page)
        }

    override suspend fun loadGuide(view: String, page: Int): ForumPage =
        withContext(Dispatchers.IO) {
            val url = "$baseHost/forum.php?mod=guide&view=$view&page=$page&t=${System.currentTimeMillis()}"
            MobileForumParser.parse(fetch(url), url, page)
        }

    override suspend fun loadThreadDetail(tid: Int, page: Int, noCache: Boolean): ThreadDetail =
        withContext(Dispatchers.IO) {
            val cacheBust = if (noCache) "&t=${System.currentTimeMillis()}&mobile=2" else ""
            val url = "$baseHost/forum.php?mod=viewthread&tid=$tid&page=$page$cacheBust"
            ThreadDetailParser.parse(fetch(url), url, tid, page)
        }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            WebCookies.cookieHeader(url)?.let { setRequestProperty("Cookie", it) }
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (GenericPageClient.isBlockedPage(html)) {
                throw java.io.IOException("\u8bbf\u95ee\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5")
            }
            if (GenericPageClient.isLoginPage(html)) {
                throw java.io.IOException("\u767b\u5f55\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55")
            }
            return html
        } finally {
            conn.disconnect()
        }
    }
}
