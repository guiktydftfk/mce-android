package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * 发帖 / 回帖（原生 POST Discuz 表单）。
 */
object PostActions {
    private const val BASE = "https://mce.msi.cn"

    data class Result(val ok: Boolean, val message: String?)

    /** 从页面里取 formhash */
    private fun formhash(html: String): String =
        Regex("""name="formhash"\s+value="([a-z0-9]+)""").find(html)?.groupValues?.get(1)
            ?: Regex("""formhash=([a-z0-9]+)""").find(html)?.groupValues?.get(1)
            ?: ""

    private fun encode(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
        }

    suspend fun reply(tid: Int, fid: Int, message: String): Result = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext Result(false, "回复内容不能为空")
        runCatching {
            val page = GenericPageClient.get("$BASE/forum.php?mod=viewthread&tid=$tid")
            val hash = formhash(page)
            if (hash.isEmpty()) return@withContext Result(false, "未获取到 formhash（可能未登录）")

            val body = encode(
                "formhash" to hash,
                "message" to message,
                "usesig" to "1",
                "subject" to "",
            )
            val url = "$BASE/forum.php?mod=post&action=reply&fid=$fid&tid=$tid" +
                "&replysubmit=yes&mobile=2"
            val resp = GenericPageClient.httpPost(url, body, null)
            val ok = !resp.contains("登录后") && !resp.contains("请输入内容")
            Result(ok, if (ok) "回复成功" else extractMessage(resp))
        }.getOrElse { Result(false, it.message) }
    }

    suspend fun newThread(fid: Int, subject: String, message: String): Result =
        withContext(Dispatchers.IO) {
            if (subject.isBlank()) return@withContext Result(false, "标题不能为空")
            if (message.isBlank()) return@withContext Result(false, "内容不能为空")
            runCatching {
                val forumPage = GenericPageClient.get("$BASE/forum.php?mod=forumdisplay&fid=$fid")
                val hash = formhash(forumPage)
                if (hash.isEmpty()) return@withContext Result(false, "未获取到 formhash（可能未登录）")

                val body = encode(
                    "formhash" to hash,
                    "subject" to subject,
                    "message" to message,
                    "usesig" to "1",
                    "posttime" to (System.currentTimeMillis() / 1000).toString(),
                )
                val url = "$BASE/forum.php?mod=post&action=newthread&fid=$fid" +
                    "&topicsubmit=yes&mobile=2"
                val resp = GenericPageClient.httpPost(url, body, null)
                val ok = !resp.contains("登录后") && !resp.contains("标题不能为空")
                Result(ok, if (ok) "发布成功" else extractMessage(resp))
            }.getOrElse { Result(false, it.message) }
        }

    private fun extractMessage(html: String): String? {
        val text = Jsoup.parse(html).text()
        Regex("""(?:登录|需要|请输入|抱歉|您)[^。]{0,40}""").find(text)?.let { return it.value.take(60) }
        return null
    }
}
