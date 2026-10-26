package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Forum repository: all requests go through GenericPageClient
 * (single network stack, cookies saved on every redirect hop).
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
        val html = GenericPageClient.get(url)
        if (GenericPageClient.isBlockedPage(html)) {
            throw IOException("\u8bbf\u95ee\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5")
        }
        if (GenericPageClient.isLoginPage(html)) {
            throw IOException("\u9700\u8981\u767b\u5f55\uff0c\u8bf7\u5148\u767b\u5f55\u540e\u518d\u8bd5")
        }
        return html
    }
}
