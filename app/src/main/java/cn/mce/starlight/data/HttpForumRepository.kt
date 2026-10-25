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

    override suspend fun loadGuide(page: Int): ForumPage = withContext(Dispatchers.IO) {
        val url = "$baseHost/forum.php?mod=guide&view=newthread&page=$page"
        MobileForumParser.parse(fetch(url), url, page)
    }

    override suspend fun loadThreadDetail(tid: Int, page: Int): ThreadDetail =
        withContext(Dispatchers.IO) {
            val url = "$baseHost/forum.php?mod=viewthread&tid=$tid&page=$page"
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
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
