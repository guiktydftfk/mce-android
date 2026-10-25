package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * 发帖 / 回帖（按 Discuz 接口逻辑实现）。
 *
 * 发帖：GET forum.php?mod=post&action=newthread&fid=X 取 formhash + 主题分类(typeid)
 *       GET forum.php?mod=forumdisplay&fid=X 取 formhash（备用）
 *       POST forum.php?mod=post&action=newthread&fid=X&topicsubmit=yes&mobile=2
 *            字段 formhash / typeid / subject / message / usesig / posttime
 * 回帖：POST forum.php?mod=post&action=reply&fid=X&tid=Y&replysubmit=yes&mobile=2
 *            字段 formhash / message / usesig / reppost（回复某楼层）
 */
object PostActions {
    private const val BASE = "https://mce.msi.cn"

    data class Result(val ok: Boolean, val message: String?)

    data class Category(val id: String, val name: String)

    data class ComposeForm(
        val fid: Int,
        val formhash: String,
        val categories: List<Category>,
    )

    private fun formhash(html: String): String =
        Regex("""name="formhash"\s+value="([a-z0-9]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""formhash=([a-z0-9]+)""").find(html)?.groupValues?.get(1)
            ?: ""

    private fun encode(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
        }

    /** 拉取发帖页：formhash + 主题分类 */
    suspend fun loadComposeForm(fid: Int): ComposeForm? = withContext(Dispatchers.IO) {
        runCatching {
            val html = GenericPageClient.get("$BASE/forum.php?mod=post&action=newthread&fid=$fid")
            val doc = Jsoup.parse(html, BASE)
            val hash = formhash(html)
            if (hash.isEmpty()) return@runCatching null

            val categories = doc.select("select[name=typeid] option")
                .mapNotNull { opt ->
                    val id = opt.attr("value")
                    val name = opt.text().trim()
                    if (id.isBlank() || id == "0" || name.contains("选择")) null
                    else Category(id, name)
                }

            ComposeForm(fid = fid, formhash = hash, categories = categories)
        }.getOrNull()
    }

    suspend fun newThread(
        fid: Int,
        subject: String,
        message: String,
        typeid: String? = null,
    ): Result = withContext(Dispatchers.IO) {
        if (subject.isBlank()) return@withContext Result(false, "标题不能为空")
        if (message.isBlank()) return@withContext Result(false, "内容不能为空")
        runCatching {
            val form = loadComposeForm(fid)
            val hash = form?.formhash ?: formhash(
                GenericPageClient.get("$BASE/forum.php?mod=forumdisplay&fid=$fid"),
            )
            if (hash.isEmpty()) return@withContext Result(false, "未获取到 formhash（可能未登录）")

            val body = encode(
                "formhash" to hash,
                "typeid" to (typeid ?: "0"),
                "subject" to subject,
                "message" to message,
                "usesig" to "1",
                "posttime" to (System.currentTimeMillis() / 1000).toString(),
            )
            val url = "$BASE/forum.php?mod=post&action=newthread&fid=$fid&topicsubmit=yes&mobile=2"
            val resp = GenericPageClient.httpPost(url, body, null)
            val ok = !resp.contains("登录后") && !resp.contains("标题不能为空") && !resp.contains("抱歉")
            Result(ok, if (ok) "发布成功" else extractMessage(resp))
        }.getOrElse { Result(false, it.message) }
    }

    suspend fun reply(tid: Int, fid: Int, message: String, replyToPid: Int? = null): Result =
        withContext(Dispatchers.IO) {
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
                    "reppost" to (replyToPid?.toString() ?: ""),
                )
                val url = "$BASE/forum.php?mod=post&action=reply&fid=$fid&tid=$tid&replysubmit=yes&mobile=2"
                val resp = GenericPageClient.httpPost(url, body, null)
                val ok = !resp.contains("登录后") && !resp.contains("请输入内容")
                Result(ok, if (ok) "回复成功" else extractMessage(resp))
            }.getOrElse { Result(false, it.message) }
        }

    /** 收藏帖子：GET home.php?mod=spacecp&ac=favorite&type=thread&id=X&formhash=Y */
    suspend fun favorite(tid: Int): Result = withContext(Dispatchers.IO) {
        runCatching {
            val page = GenericPageClient.get("$BASE/forum.php?mod=viewthread&tid=$tid")
            val hash = formhash(page)
            if (hash.isEmpty()) return@withContext Result(false, "未获取到 formhash（可能未登录）")
            val alreadyFav = page.contains("action=unfavorite") ||
                page.contains("取消收藏")
            if (alreadyFav) return@withContext Result(false, "已经收藏过了")
            val url = "$BASE/home.php?mod=spacecp&ac=favorite&type=thread&id=$tid" +
                "&formhash=$hash&handlekey=favorite"
            GenericPageClient.get(url)
            Result(true, "已收藏")
        }.getOrElse { Result(false, it.message) }
    }
    private fun extractMessage(html: String): String? {
        val text = Jsoup.parse(html).text()
        Regex("""(?:登录|需要|请输入|抱歉|您)[^。]{0,40}""").find(text)?.let { return it.value.take(60) }
        return null
    }
}
