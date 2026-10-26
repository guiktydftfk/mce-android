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
    )

    suspend fun loadForm(tid: Int, pid: Int): Form? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/forum.php?mod=misc&action=rate&tid=$tid&pid=$pid" +
                "&infloat=yes&handlekey=rate&inajax=1&ajaxtarget=fwin_content_rate"
            val raw = GenericPageClient.get(url)
            val html = raw.substringAfter("<![CDATA[").substringBefore("]]>")
            if (html.isBlank()) return@runCatching null

            val doc = Jsoup.parse(html, BASE)
            fun hidden(name: String) = doc.selectFirst("input[name=$name]")?.attr("value").orEmpty()

            val formhash = hidden("formhash")
            if (formhash.isEmpty()) return@runCatching null

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

            Form(
                tid = tid,
                pid = pid,
                formhash = formhash,
                referer = hidden("referer"),
                handlekey = hidden("handlekey").ifBlank { "rate" },
                scoreField = scoreField,
                scoreMax = max,
                scoreMin = min,
                reasons = reasons,
            )
        }.getOrNull()
    }

    suspend fun submit(form: Form, score: String, reason: String): Pair<Boolean, String> =
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
                val url = "$BASE/forum.php?mod=misc&action=rate&ratesubmit=yes&infloat=yes"
                val resp = GenericPageClient.httpPost(url, body, null)
                // v0.3.3 批 4：旧实现只认 "error"/"抱歉"，"您还可以评分 0 次""不能给自己评分" 等
                // 中文失败文案会被判成成功；现在统一走 DiscuzResponse。
                DiscuzResponse.judge(
                    resp,
                    successText = "\u8bc4\u5206\u6210\u529f",
                    unknownText = "\u8bc4\u5206\u7ed3\u679c\u672a\u786e\u8ba4\uff0c\u8bf7\u5237\u65b0\u67e5\u770b",
                )
            }.getOrDefault(false to "\u7f51\u7edc\u9519\u8bef")
        }
}
