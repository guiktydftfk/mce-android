package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * v0.4.7：举报（主题 / 楼层）。
 *
 * 旧实现直接打开 `forum.php?mod=misc&action=report&tid=..`，缺少 fid/formhash/reppost
 * 且 Discuz 举报页是 AJAX 片段，WebView 里表现为空白页（用户反馈「举报按钮无效」）。
 *
 * 现改为原生流程：
 *  1. GET 举报页，解析出真实表单（action 地址、隐藏字段、举报理由 id/文案）；
 *  2. App 内弹窗选择理由 + 填写说明；
 *  3. 按表单字段 POST 提交，并用 DiscuzResponse 判定结果。
 */
object ReportClient {

    data class ReportForm(
        /** 表单提交地址（已补 inajax=1） */
        val actionUrl: String,
        /** 表单隐藏字段（formhash/tid/fid/reppost/referer 等） */
        val hidden: List<Pair<String, String>>,
        /** 举报理由：id -> 文案 */
        val reasons: List<Pair<String, String>>,
    )

    private const val BASE = cn.mce.starlight.data.Net.BASE

    suspend fun loadForm(tid: Int, fid: Int?, pid: Int?): ReportForm? = withContext(Dispatchers.IO) {
        runCatching {
            val hash = FormhashStore.get()
            val url = buildString {
                append(BASE)
                append("/forum.php?mod=misc&action=report&tid=").append(tid)
                if (fid != null) append("&fid=").append(fid)
                if (pid != null) append("&reppost=").append(pid)
                if (hash.isNotBlank()) append("&formhash=").append(hash)
                append("&mobile=no")
            }
            val html = GenericPageClient.get(url)
            val doc = Jsoup.parse(html, BASE)

            // 优先找举报表单；Discuz 表单 id 通常为 reportform
            val form: Element? = doc.selectFirst("form#reportform")
                ?: doc.select("form").firstOrNull { f ->
                    val a = f.attr("action")
                    a.contains("action=report", true)
                }
            if (form != null) {
                val actionRaw = form.attr("action").ifBlank { url }
                val action = if (actionRaw.startsWith("http")) actionRaw
                else BASE + "/" + actionRaw.trimStart('/')
                val hidden = mutableListOf<Pair<String, String>>()
                form.select("input[type=hidden]").forEach { i ->
                    val n = i.attr("name")
                    if (n.isNotBlank() && n != "formhash") hidden += n to i.attr("value")
                }
                if (hash.isNotBlank()) hidden += "formhash" to hash
                val reasons = mutableListOf<Pair<String, String>>()
                form.select("input[name=reasons[]], input[name*=reasons]").forEach { i ->
                    val id = i.attr("value")
                    if (id.isNotBlank()) {
                        val label = (i.parent()?.text() ?: "").trim().ifBlank { i.attr("title") }
                        reasons += id to label.ifBlank { "理由 $id" }
                    }
                }
                val finalAction = if (action.contains("inajax", true)) action
                else action + (if (action.contains("?")) "&" else "?") + "inajax=1"
                return@runCatching ReportForm(
                    actionUrl = finalAction,
                    hidden = hidden,
                    reasons = if (reasons.isEmpty()) defaultReasons() else reasons,
                )
            }
            // 首页未能拿到表单时，退回默认理由（仍走标准提交地址）
            ReportForm(
                actionUrl = "$BASE/forum.php?mod=misc&action=report&reportsubmit=yes&inajax=1",
                hidden = buildList {
                    if (hash.isNotBlank()) add("formhash" to hash)
                    add("tid" to tid.toString())
                    if (fid != null) add("fid" to fid.toString())
                    add("reppost" to (pid?.toString() ?: "0"))
                    add("referer" to "$BASE/forum.php?mod=viewthread&tid=$tid")
                },
                reasons = defaultReasons(),
            )
        }.getOrNull()
    }

    suspend fun submit(form: ReportForm, reasonId: String, message: String): PostActions.Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val hash = FormhashStore.get()
                val pairs = mutableListOf<Pair<String, String>>()
                pairs += form.hidden
                pairs += "reasons[]" to reasonId
                pairs += "message" to message.trim()
                if (hash.isNotBlank() && pairs.none { it.first == "formhash" }) pairs += "formhash" to hash
                val body = pairs.joinToString("&") { (k, v) -> encode(k) + "=" + encode(v) }
                val resp = GenericPageClient.httpPost(form.actionUrl, body, null)
                val (ok, msg) = DiscuzResponse.judge(resp, "感谢您的举报")
                // v0.4.7：记录原始回包，便于设置→最近错误日志里排查举报失败原因
                cn.mce.starlight.core.DiagLog.record(
                    "report",
                    "ok=" + ok + " msg=" + msg + " raw=" + resp.replace("\\n", " ").take(300),
                )
                PostActions.Result(ok, msg)
            }.getOrElse { PostActions.Result(false, it.message ?: "举报提交失败") }
        }

    private fun defaultReasons(): List<Pair<String, String>> = listOf(
        "1" to "广告 / 垃圾信息",
        "2" to "违规或不当内容",
        "3" to "人身攻击 / 辱骂",
        "4" to "其他（请在下方说明）",
    )

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
