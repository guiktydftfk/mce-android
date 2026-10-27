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
 * poll:   GET forum.php?mod=post&action=newthread&fid=X&special=1  (mobile 模板「发投票」)
 *         POST 同发帖地址 + special=1 / polls=yes / tpolloption=2 /
 *              polloptions（每行一个选项）/ maxchoices / expiration /
 *              visibilitypoll（投票后结果可见）/ overt（公开投票参与人）
 * reply: POST forum.php?mod=post&action=reply&fid=X&tid=Y&replysubmit=yes (+reppost)
 * upload: POST misc.php?mod=swfupload&action=swfupload&operation=upload&simple=1&type=image&fid=X (Filedata)
 */
object PostActions {
    private const val BASE = Net.BASE
    // v0.3.3 批 3：正则常量（避免每次调用编译）
    private val aidJsonRegex = Regex(""""aid"\s*:\s*"?(\d+)""")
    private val aidQueryRegex = Regex("""aid=(\d+)""")

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

    /** 投票参数（对应移动模板「发投票」表单） */
    data class Poll(
        val choices: List<String>,
        val maxChoices: Int? = null,
        val expireDays: Int? = null,
        /** visibilitypoll：投票后结果可见 */
        val resultVisible: Boolean = false,
        /** overt：公开投票参与人 */
        val publicVoters: Boolean = false,
    ) {
        val validChoices: List<String>
            get() = choices.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(MAX_CHOICES)

        companion object {
            const val MAX_CHOICES = 20
        }
    }

    private fun formhash(html: String): String = Net.formhashOf(html).orEmpty()

    private fun encode(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
        }

    /** Load compose form: formhash + topic categories (poll = 使用「发投票」表单) */
    suspend fun loadComposeForm(fid: Int, poll: Boolean = false): ComposeForm? =
        withContext(Dispatchers.IO) {
            runCatching {
                val special = if (poll) "&special=1" else ""
                val html = GenericPageClient.get("$BASE/forum.php?mod=post&action=newthread&fid=$fid$special")
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

    /** 最近一次上传失败时服务端返回的原文（用于排查/提示） */
    var lastUploadError: String? = null

    /** Upload one image, returns attachment id (aid) or null */
    suspend fun uploadImage(fid: Int, bytes: ByteArray, filename: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                // v0.3.8：补 formhash / mobile（手机模板 swfupload 会校验表单令牌与来路）
                val uploadHash = FormhashStore.get()
                val url = "$BASE/misc.php?mod=swfupload&action=swfupload&operation=upload" +
                    "&simple=1&type=image&fid=$fid&mobile=2&formhash=$uploadHash"
                val boundary = "----MCEBoundary${System.currentTimeMillis()}"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", HttpForumRepository.MOBILE_UA)
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("X-Requested-With", "XMLHttpRequest")
                    setRequestProperty("Referer", "$BASE/forum.php?mod=post&action=reply&fid=$fid")
                    cn.mce.starlight.core.WebCookies.cookieHeader()?.let {
                        setRequestProperty("Cookie", it)
                    }
                }
                DataOutputStream(conn.outputStream).use { out ->
                    fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
                    fun field(name: String, value: String) {
                        write("--$boundary\r\n")
                        write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                        write("$value\r\n")
                    }
                    if (uploadHash.isNotEmpty()) field("formhash", uploadHash)
                    cn.mce.starlight.core.SessionStore.uid?.let { field("uid", it.toString()) }
                    write("--$boundary\r\n")
                    write("Content-Disposition: form-data; name=\"Filedata\"; filename=\"$filename\"\r\n")
                    write("Content-Type: image/jpeg\r\n\r\n")
                    out.write(bytes)
                    write("\r\n--$boundary--\r\n")
                    out.flush()
                }
                val code = runCatching { conn.responseCode }.getOrDefault(-1)
                val resp = runCatching {
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                }.getOrElse { "" }
                conn.disconnect()
                val aid = aidJsonRegex.find(resp)?.groupValues?.get(1)
                    ?: aidQueryRegex.find(resp)?.groupValues?.get(1)
                if (aid == null) lastUploadError = "HTTP $code " + resp.take(180)
                aid
            }.getOrNull()
        }

    /**
     * 参与投票（v0.3.3）。
     * 提交地址取自帖内 `form#poll` 的 action；选项用 Discuz 的数组参数 `pollanswers[]`。
     * 成功判定：响应里出现已投标记（`poll_done` / 「您已经投过票」）——
     * 注意模板 JS 里本身就有 `oksmg = '投票成功'` 字样，不能只靠这个字符串判定。
     */
    suspend fun votePoll(
        action: String,
        formhash: String,
        optionIds: List<String>,
    ): Result = withContext(Dispatchers.IO) {
        if (optionIds.isEmpty()) return@withContext Result(false, "\u8bf7\u5148\u9009\u62e9\u6295\u7968\u9009\u9879")
        runCatching {
            val url = if (action.startsWith("http")) action else "$BASE/$action"
            val body = buildString {
                append("formhash=").append(URLEncoder.encode(formhash, "UTF-8"))
                append("&pollsubmit=").append(URLEncoder.encode("\u63d0\u4ea4", "UTF-8"))
                optionIds.forEach { id ->
                    append("&pollanswers%5B%5D=").append(URLEncoder.encode(id, "UTF-8"))
                }
            }
            val resp = GenericPageClient.httpPost(url, body, null)
            when {
                resp.contains("poll_done") || resp.contains("\u60a8\u5df2\u7ecf\u6295\u8fc7\u7968") ->
                    Result(true, "\u6295\u7968\u6210\u529f")
                else -> {
                    val failure = DiscuzResponse.failureMessage(resp)
                    if (failure != null) Result(false, failure)
                    else Result(false, "\u6295\u7968\u7ed3\u679c\u672a\u786e\u8ba4\uff0c\u8bf7\u5237\u65b0\u67e5\u770b")
                }
            }
        }.getOrElse { Result(false, "\u6295\u7968\u5931\u8d25\uff1a" + (it.message ?: "\u7f51\u7edc\u9519\u8bef")) }
    }

    /** 发送私信（v0.3.4）：POST 到会话页表单 action */
    suspend fun sendPm(
        action: String,
        formhash: String,
        touid: Int,
        message: String,
    ): Result = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext Result(false, "\u6d88\u606f\u4e0d\u80fd\u4e3a\u7a7a")
        runCatching {
            val url = if (action.startsWith("http")) action else "$BASE/$action"
            val body = buildString {
                append("formhash=").append(URLEncoder.encode(formhash, "UTF-8"))
                append("&touid=").append(touid)
                append("&message=").append(URLEncoder.encode(message, "UTF-8"))
                append("&pmsubmit=").append(URLEncoder.encode("\u56de\u590d", "UTF-8"))
            }
            val resp = GenericPageClient.httpPost(url, body, null)
            when {
                resp.contains("\u53d1\u9001\u6210\u529f") || resp.contains("show_success") ->
                    Result(true, "\u5df2\u53d1\u9001")
                else -> {
                    val failure = DiscuzResponse.failureMessage(resp)
                    if (failure != null) Result(false, failure)
                    else Result(true, "\u5df2\u53d1\u9001")
                }
            }
        }.getOrElse { Result(false, "\u53d1\u9001\u5931\u8d25\uff1a" + (it.message ?: "\u7f51\u7edc\u9519\u8bef")) }
    }

    suspend fun newThread(
        fid: Int,
        subject: String,
        message: String,
        typeid: String? = null,
        aids: List<String> = emptyList(),
        options: Options = Options(),
        poll: Poll? = null,
    ): Result = withContext(Dispatchers.IO) {
        if (subject.isBlank()) return@withContext Result(false, "\u6807\u9898\u4e0d\u80fd\u4e3a\u7a7a")
        if (message.isBlank()) return@withContext Result(false, "\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a")
        if (poll != null && poll.validChoices.size < 2) {
            return@withContext Result(false, "\u6295\u7968\u81f3\u5c11\u9700\u8981 2 \u4e2a\u9009\u9879")
        }
        runCatching {
            // formhash 由 UI 预热到 FormhashStore，避免重复抓页面
            val hash = FormhashStore.get()
            if (hash.isEmpty()) {
                return@withContext Result(false, "\u672a\u83b7\u53d6 formhash\uff08\u53ef\u80fd\u672a\u767b\u5f55\uff09")
            }

            val fields = mutableListOf(
                "formhash" to hash,
                "typeid" to (typeid ?: "0"),
                "subject" to subject,
                "message" to message,
                "posttime" to (System.currentTimeMillis() / 1000).toString(),
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

            // 投票：special=1 + polls=yes；tpolloption=2 表示单框（换行分隔）模式
            if (poll != null) {
                fields += "special" to "1"
                fields += "polls" to "yes"
                fields += "tpolloption" to "2"
                fields += "polloptions" to poll.validChoices.joinToString("\n")
                poll.maxChoices?.let { if (it > 0) fields += "maxchoices" to it.toString() }
                poll.expireDays?.let { if (it > 0) fields += "expiration" to it.toString() }
                if (poll.resultVisible) fields += "visibilitypoll" to "1"
                if (poll.publicVoters) fields += "overt" to "1"
            }

            val body = fields.joinToString("&") {
                URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
            }
            val url = "$BASE/forum.php?mod=post&action=newthread&fid=$fid&topicsubmit=yes&mobile=2"
            val resp = GenericPageClient.httpPost(url, body, null)
            // v0.3.3 批 4：统一走 DiscuzResponse（显式成功标记 + 失败原文）
            val (ok, msg) = DiscuzResponse.judge(resp, "\u53d1\u5e03\u6210\u529f")
            Result(ok, msg)
        }.getOrElse { Result(false, it.message) }
    }

    suspend fun reply(
        tid: Int,
        fid: Int,
        message: String,
        replyToPid: Int? = null,
        /** v0.3.8：回帖附图（先 uploadImage 拿到 aid，再随回复一起提交） */
        aids: List<String> = emptyList(),
    ): Result =
        withContext(Dispatchers.IO) {
            if (message.isBlank() && aids.isEmpty()) {
                return@withContext Result(false, "\u56de\u590d\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a")
            }
            runCatching {
                val hash = FormhashStore.get()
                if (hash.isEmpty()) {
                    return@withContext Result(false, "\u672a\u83b7\u53d6 formhash\uff08\u53ef\u80fd\u672a\u767b\u5f55\uff09")
                }

                val fields = mutableListOf(
                    "formhash" to hash,
                    "message" to message.ifBlank { "\u56de\u5e16\u9644\u56fe" },
                    "usesig" to "1",
                    "subject" to "",
                    "reppost" to (replyToPid?.toString() ?: ""),
                )
                // 已上传的附件要在回帖请求里登记，否则服务端会当孤儿附件
                aids.forEach { aid -> fields += "attachnew[$aid]" to "1" }
                val body = encode(*fields.toTypedArray())
                val url = "$BASE/forum.php?mod=post&action=reply&fid=$fid&tid=$tid&replysubmit=yes&mobile=2"
                val resp = GenericPageClient.httpPost(url, body, null)
                // v0.3.3 批 4：旧判定 `!resp.contains("登录后") && !resp.contains("请输入内容")` 几乎恒真，
                // 失败（来路不正确 / 两次发表间隔 / 内容过短…）也会提示"回复成功"。
                val (ok, msg) = DiscuzResponse.judge(resp, "\u56de\u590d\u6210\u529f")
                Result(ok, msg)
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
            val resp = GenericPageClient.get(
                "$BASE/home.php?mod=spacecp&ac=favorite&type=thread&id=$tid&formhash=$hash&handlekey=favorite",
            )
            // v0.3.3 批 4：以前收藏后无条件返回成功，现在按响应判定
            val (ok, msg) = DiscuzResponse.judge(resp, "\u5df2\u6536\u85cf")
            Result(ok, msg)
        }.getOrElse { Result(false, it.message) }
    }
}
