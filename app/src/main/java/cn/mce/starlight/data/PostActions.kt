package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Compose / reply actions implemented against Discuz endpoints.
 *
 * newthread: POST forum.php?mod=post&action=newthread&fid=X&topicsubmit=yes
 *   core: formhash / typeid / subject / message / usesig / posttime
 *   attachments: attachnew[<aid>]=1 (uploaded via misc.php?mod=swfupload&operation=upload)
 *   options: hiddenreplies / ordertype / allownoticeauthor / smileyoff / parseurloff / usesig
 *   reply credit: replycredit_times / replycredit_extcredits
 *   draft: save=1
 * reply: POST forum.php?mod=post&action=reply&fid=X&tid=Y&replysubmit=yes (+reppost)
 * upload: POST misc.php?mod=swfupload&action=swfupload&operation=upload&simple=1&type=image&fid=X (Filedata)
 */
object PostActions {
    private const val BASE = "https://mce.msi.cn"

    data class Result(val ok: Boolean, val message: String?)

    data class Category(val id: String, val name: String)

    data class ComposeForm(
        val fid: Int,
        val formhash: String,
        val categories: List<Category>,
        val posttime: String?,
    )

    data class Options(
        val hiddenReplies: Boolean = false,
        val orderType: Boolean = false,
        val noticeAuthor: Boolean = true,
        val useSign: Boolean = true,
        val disableSmiley: Boolean = false,
        val parseUrlOff: Boolean = false,
        val replyCreditTimes: String = "",
        val replyCreditValue: String = "",
        val draft: Boolean = false,
    )

    private fun formhash(html: String): String =
        Regex("""name="formhash"\s+value="([a-z0-9]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""formhash=([a-z0-9]+)""").find(html)?.groupValues?.get(1)
            ?: ""

    private fun encode(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
        }

    /** Load compose form: formhash + topic categories */
    suspend fun loadComposeForm(fid: Int): ComposeForm? = withContext(Dispatchers.IO) {
        runCatching {
            val html = GenericPageClient.get("$BASE/forum.php?mod=post&action=newthread&fid=$fid")
            val doc = Jsoup.parse(html, BASE)
            val hash = formhash(html)
            if (hash.isEmpty()) return@runCatching null

            val categories = doc.select("select[name=typeid] option").mapNotNull { opt ->
                val id = opt.attr("value")
                val name = opt.text().trim()
                if (id.isBlank() || id == "0" || name.contains("\u9009\u62e9")) null
                else Category(id, name)
            }

            val posttime = doc.selectFirst("input[name=posttime]")?.attr("value")

            ComposeForm(fid, hash, categories, posttime)
        }.getOrNull()
    }

    /** Upload one image, returns attachment id (aid) or null */
    suspend fun uploadImage(fid: Int, bytes: ByteArray, filename: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE/misc.php?mod=swfupload&action=swfupload&operation=upload" +
                    "&simple=1&type=image&fid=$fid"
                val boundary = "----MCEBoundary${System.currentTimeMillis()}"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", HttpForumRepository.MOBILE_UA)
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    cn.mce.starlight.core.WebCookies.cookieHeader()?.let {
                        setRequestProperty("Cookie", it)
                    }
                }
                DataOutputStream(conn.outputStream).use { out ->
                    fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
                    write("--$boundary\r\n")
                    write("Content-Disposition: form-data; name=\"Filedata\"; filename=\"$filename\"\r\n")
                    write("Content-Type: application/octet-stream\r\n\r\n")
                    out.write(bytes)
                    write("\r\n--$boundary--\r\n")
                    out.flush()
                }
                val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                Regex(""""aid"\s*:\s*"?(\d+)""").find(resp)?.groupValues?.get(1)
                    ?: Regex("""aid=(\d+)""").find(resp)?.groupValues?.get(1)
            }.getOrNull()
        }

    suspend fun newThread(
        fid: Int,
        subject: String,
        message: String,
        typeid: String? = null,
        aids: List<String> = emptyList(),
        options: Options = Options(),
    ): Result = withContext(Dispatchers.IO) {
        if (subject.isBlank()) return@withContext Result(false, "\u6807\u9898\u4e0d\u80fd\u4e3a\u7a7a")
        if (message.isBlank()) return@withContext Result(false, "\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a")
        runCatching {
            val form = loadComposeForm(fid)
            val hash = form?.formhash ?: formhash(
                GenericPageClient.get("$BASE/forum.php?mod=forumdisplay&fid=$fid"),
            )
            if (hash.isEmpty()) {
                return@withContext Result(false, "\u672a\u83b7\u53d6 formhash\uff08\u53ef\u80fd\u672a\u767b\u5f55\uff09")
            }

            val fields = mutableListOf(
                "formhash" to hash,
                "typeid" to (typeid ?: "0"),
                "subject" to subject,
                "message" to message,
                "posttime" to (form?.posttime ?: (System.currentTimeMillis() / 1000).toString()),
                "wysiwyg" to "0",
                "usesig" to if (options.useSign) "1" else "",
            )
            if (options.hiddenReplies) fields += "hiddenreplies" to "1"
            if (options.orderType) fields += "ordertype" to "1"
            if (options.noticeAuthor) fields += "allownoticeauthor" to "1"
            if (options.disableSmiley) fields += "smileyoff" to "1"
            if (options.parseUrlOff) fields += "parseurloff" to "1"
            if (options.replyCreditTimes.isNotBlank()) {
                fields += "replycredit_times" to options.replyCreditTimes
                fields += "replycredit_extcredits" to options.replyCreditValue.ifBlank { "0" }
            }
            if (options.draft) fields += "save" to "1"
            aids.forEach { aid -> fields += "attachnew[$aid]" to "1" }

            val body = fields.joinToString("&") {
                URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
            }
            val url = "$BASE/forum.php?mod=post&action=newthread&fid=$fid&topicsubmit=yes&mobile=2"
            val resp = GenericPageClient.httpPost(url, body, null)
            val ok = !resp.contains("\u767b\u5f55\u540e") && !resp.contains("\u6807\u9898\u4e0d\u80fd\u4e3a\u7a7a")
            Result(ok, if (ok) "\u53d1\u5e03\u6210\u529f" else extractMessage(resp))
        }.getOrElse { Result(false, it.message) }
    }

    suspend fun reply(tid: Int, fid: Int, message: String, replyToPid: Int? = null): Result =
        withContext(Dispatchers.IO) {
            if (message.isBlank()) return@withContext Result(false, "\u56de\u590d\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a")
            runCatching {
                val page = GenericPageClient.get("$BASE/forum.php?mod=viewthread&tid=$tid")
                val hash = formhash(page)
                if (hash.isEmpty()) {
                    return@withContext Result(false, "\u672a\u83b7\u53d6 formhash\uff08\u53ef\u80fd\u672a\u767b\u5f55\uff09")
                }

                val body = encode(
                    "formhash" to hash,
                    "message" to message,
                    "usesig" to "1",
                    "subject" to "",
                    "reppost" to (replyToPid?.toString() ?: ""),
                )
                val url = "$BASE/forum.php?mod=post&action=reply&fid=$fid&tid=$tid&replysubmit=yes&mobile=2"
                val resp = GenericPageClient.httpPost(url, body, null)
                val ok = !resp.contains("\u767b\u5f55\u540e") && !resp.contains("\u8bf7\u8f93\u5165\u5185\u5bb9")
                Result(ok, if (ok) "\u56de\u590d\u6210\u529f" else extractMessage(resp))
            }.getOrElse { Result(false, it.message) }
        }

    /** Favorite a thread */
    suspend fun favorite(tid: Int): Result = withContext(Dispatchers.IO) {
        runCatching {
            val page = GenericPageClient.get("$BASE/forum.php?mod=viewthread&tid=$tid")
            val hash = formhash(page)
            if (hash.isEmpty()) {
                return@withContext Result(false, "\u672a\u83b7\u53d6 formhash\uff08\u53ef\u80fd\u672a\u767b\u5f55\uff09")
            }
            if (page.contains("action=unfavorite")) {
                return@withContext Result(false, "\u5df2\u7ecf\u6536\u85cf\u8fc7\u4e86")
            }
            GenericPageClient.get(
                "$BASE/home.php?mod=spacecp&ac=favorite&type=thread&id=$tid&formhash=$hash&handlekey=favorite",
            )
            Result(true, "\u5df2\u6536\u85cf")
        }.getOrElse { Result(false, it.message) }
    }

    private fun extractMessage(html: String): String? {
        val text = Jsoup.parse(html).text()
        Regex("""(?:\u767b\u5f55|\u9700\u8981|\u8bf7\u8f93\u5165|\u62b1\u6b49|\u60a8)[^\u3002]{0,40}""")
            .find(text)?.let { return it.value.take(60) }
        return null
    }
}
