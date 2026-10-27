package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * 评分 / 赞赏（实测逻辑）：
 *   GET  forum.php?mod=misc&action=rate&tid=X&pid=Y&infloat=yes&handlekey=rate&inajax=1
 *        → 返回浮窗表单（#rateform）
 *   POST forum.php?mod=misc&action=rate&ratesubmit=yes&infloat=yes
 *        字段：formhash / tid / pid / referer / handlekey=rate / score1 / reason / sendreasonpm
 *   分值区间来自页面（示例：赞赏值 1~2，今日剩余 8）
 */
object RateClient {
    private const val BASE = Net.BASE
    // v0.3.3 批 3：正则常量
    private val rangeRegex = Regex("""\d+\s*[~-]\s*\d+""")
    private val rangeMinRegex = Regex("""(\d+)\s*[~-]""")
    private val rangeMaxRegex = Regex("""[~-]\s*(\d+)""")

    data class Form(
        val tid: Int,
        val pid: Int,
        val formhash: String,
        val referer: String,
        val handlekey: String,
        val scoreField: String,
        val scoreMax: Int,
        val scoreMin: Int,
        val reasons: List<String>,
        /** 表单自己的 action（v0.3.9：带着 inajax/handlekey 等参数，直接用它提交才不会走错分支） */
        val action: String = "",
    )

    /**
     * 打开评分/赞赏弹窗的结果（v0.3.9）：
     * 成功时 [form] 非空；失败时 [message] 是服务端原文
     * （如「抱歉，您不能对同一个帖子重复评分」「您还可以评分 0 次」），不再一律显示「可能未登录」。
     */
    data class LoadOutcome(val form: Form? = null, val message: String? = null)

    suspend fun loadForm(tid: Int, pid: Int): LoadOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/forum.php?mod=misc&action=rate&tid=$tid&pid=$pid" +
                "&infloat=yes&handlekey=rate&inajax=1&ajaxtarget=fwin_content_rate"
            val raw = GenericPageClient.get(url)
            val html = raw.substringAfter("<![CDATA[").substringBefore("]]>")
            if (html.isBlank()) return@runCatching LoadOutcome(message = "评分表单为空（可能未登录）")

            val doc = Jsoup.parse(html, BASE)
            fun hidden(name: String) = doc.selectFirst("input[name=$name]")?.attr("value").orEmpty()

            val formhash = hidden("formhash")
            if (formhash.isEmpty()) {
                // 没有表单 = 服务端拒绝打开弹窗（重复评分 / 次数用尽 / 无权…），把原文带出去
                return@runCatching LoadOutcome(
                    message = DiscuzResponse.failureMessage(raw)
                        ?: DiscuzResponse.failureMessage(html)
                        ?: "无法打开评分表单（可能未登录或无权评分）",
                )
            }

            // v0.3.9：优先用表单自带的 action（含 handlekey / inajax 等参数）
            val formAction = doc.selectFirst("form")?.absUrl("action")?.takeIf { it.isNotBlank() }.orEmpty()

            val scoreInput = doc.selectFirst("input[name^=score]")
            val scoreField = scoreInput?.attr("name")?.takeIf { it.isNotBlank() } ?: "score1"

            // 评分区间：单元格文本形如 "1 ~ 2"
            val rangeText = doc.select("table.dt td").map { it.text() }
                .firstOrNull { rangeRegex.containsMatchIn(it) }
            val min = rangeText?.let { rangeMinRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 1
            val max = rangeText?.let { rangeMaxRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 2

            val reasons = doc.select("ul#reasonselect li, .reason_slct li").map { it.text().trim() }
                .filter { it.isNotBlank() }
                .distinct()

            LoadOutcome(
                form = Form(
                    tid = tid,
                    pid = pid,
                    formhash = formhash,
                    referer = hidden("referer"),
                    handlekey = hidden("handlekey").ifBlank { "rate" },
                    scoreField = scoreField,
                    scoreMax = max,
                    scoreMin = min,
                    reasons = reasons,
                    action = formAction,
                ),
            )
        }.getOrElse { LoadOutcome(message = "网络错误：" + (it.message ?: "请稍后重试")) }
    }

    suspend fun submit(
        form: Form,
        score: String,
        reason: String,
        /** 判定成功时给用户看的文案：赞赏 →「赞赏成功」，评分 →「评分成功」 */
        successText: String = "\u8bc4\u5206\u6210\u529f",
    ): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = listOf(
                    "formhash" to form.formhash,
                    "tid" to form.tid.toString(),
                    "pid" to form.pid.toString(),
                    "referer" to form.referer,
                    "handlekey" to form.handlekey,
                    form.scoreField to score,
                    "reason" to reason,
                    "sendreasonpm" to "on",
                    "ratesubmit" to "yes",
                ).joinToString("&") {
                    URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
                }
                // v0.3.9：必须带 inajax=1 才是「AJAX 提交」分支。
                // 旧实现直接 POST 到表单 action（不带 inajax），Discuz 会返回一整页「提示信息 —
                // 未定义操作」的 HTML，服务端根本没执行评分：既没扣赞赏值，也判定不出成功，
                // 用户只看到「评分结果未确认」。带上 inajax=1 后回包是
                //   <div class="tip"><dt id="messagetext">感谢您的参与…</dt></div>
                // 命中 DiscuzResponse 的成功标记，能明确显示「评分成功」。
                val rawUrl = form.action.ifBlank {
                    "$BASE/forum.php?mod=misc&action=rate&ratesubmit=yes&infloat=yes"
                }
                val url = if (rawUrl.contains("inajax=")) {
                    rawUrl
                } else {
                    rawUrl + (if (rawUrl.contains("?")) "&" else "?") + "inajax=1"
                }
                val resp = GenericPageClient.httpPost(url, body, null)
                // v0.3.3 批 4：旧实现只认 "error"/"抱歉"，"您还可以评分 0 次""不能给自己评分" 等
                // 中文失败文案会被判成成功；现在统一走 DiscuzResponse。
                DiscuzResponse.judge(
                    resp,
                    successText = successText,
                    unknownText = "\u8bc4\u5206\u7ed3\u679c\u672a\u786e\u8ba4\uff0c\u8bf7\u5237\u65b0\u67e5\u770b",
                )
            }.getOrDefault(false to "\u7f51\u7edc\u9519\u8bef")
        }
}
