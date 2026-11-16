package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * v0.4.8：个人空间发布 —— 日志（原生表单提交）。
 *
 * 日志发布页：`home.php?mod=spacecp&ac=blog&mobile=no`（桌面模板，手机模板无此功能）。
 * 相册上传依赖多文件 multipart 与相册 ID 状态，暂以网页页面兜底（见 UI）。
 */
object SpaceComposeClient {

    data class Field(
        val name: String,
        val value: String,
        val label: String,
        val multiline: Boolean,
        val options: List<Pair<String, String>> = emptyList(),
    )

    data class BlogForm(
        val action: String,
        val method: String,
        val fields: List<Field>,
    )

    suspend fun loadBlogForm(): BlogForm? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${Net.BASE}/home.php?mod=spacecp&ac=blog&mobile=no"
            val html = GenericPageClient.get(url, userAgent = Net.DESKTOP_UA, isolateCookies = true)
            parseBlogForm(html)
        }.getOrNull()
    }

    /** 解析日志表单（提取为可测试函数） */
    internal fun parseBlogForm(html: String): BlogForm? {
        val doc = Jsoup.parse(html, "${Net.BASE}/home.php?mod=spacecp&ac=blog")
        val form = doc.select("form").firstOrNull { f ->
            f.select("textarea[name], input[name=subject], input[name=title]").isNotEmpty()
        } ?: return null
        val action = form.absUrl("action").ifBlank { "${Net.BASE}/home.php?mod=spacecp&ac=blog&mobile=no" }
        val method = form.attr("method").ifBlank { "post" }

        fun labelOf(el: Element): String {
            val id = el.attr("id")
            if (id.isNotBlank()) {
                form.selectFirst("label[for=$id]")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return el.attr("name")
        }

        val fields = mutableListOf<Field>()
        form.select("input[name]").forEach { input ->
            val type = input.attr("type").ifBlank { "text" }.lowercase()
            when (type) {
                "submit", "button", "reset", "file" -> {}
                "hidden" -> fields += Field(input.attr("name"), input.attr("value"), "", false)
                else -> fields += Field(input.attr("name"), input.attr("value"), labelOf(input), false)
            }
        }
        form.select("textarea[name]").forEach { ta ->
            fields += Field(ta.attr("name"), ta.text(), labelOf(ta), true)
        }
        form.select("select[name]").forEach { sel ->
            val options = sel.select("option").map { it.attr("value") to it.text().trim() }
            val selected = sel.select("option[selected]").firstOrNull()?.attr("value")
                ?: sel.select("option").firstOrNull()?.attr("value").orEmpty()
            fields += Field(sel.attr("name"), selected, labelOf(sel), false, options)
        }
        return if (fields.none { it.name == "message" }) null else BlogForm(action, method, fields)
    }

    /** 提交日志；返回 (是否成功, 提示) */
    suspend fun submitBlog(form: BlogForm, values: Map<String, String>): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val merged = form.fields.associate { field ->
                field.name to (values[field.name] ?: field.value)
            }
            val body = merged.entries.joinToString("&") {
                URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
            }
            val resp = runCatching {
                if (form.method.equals("post", true)) {
                    GenericPageClient.httpPost(form.action, body, null, Net.DESKTOP_UA)
                } else {
                    GenericPageClient.get(
                        form.action + (if (form.action.contains("?")) "&" else "?") + body,
                    )
                }
            }.getOrNull() ?: return@withContext false to "网络较弱，请稍后重试"
            val ok = DiscuzResponse.isSuccess(resp) || resp.contains("日志发布成功") || resp.contains("发表成功")
            val msg = if (ok) "日志发布成功" else (DiscuzResponse.failureMessage(resp) ?: "发布失败，请稍后重试")
            ok to msg
        }

    /** 相册上传网页兜底地址 */
    fun albumPageUrl(): String = "${Net.BASE}/home.php?mod=spacecp&ac=album&mobile=no"
}
