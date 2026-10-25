package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 个人资料编辑（home.php?mod=spacecp&ac=profile）。
 *
 * 该页面被拆成多个表单（联系方式 / 个人信息 / 教育情况…），
 * 因此按 form 分组解析并逐个提交。
 */
object ProfileEditClient {
    private const val BASE = "https://mce.msi.cn"
    private const val BASE_URL = "$BASE/home.php?mod=spacecp&ac=profile"

    enum class Kind { TEXT, TEXTAREA, SELECT, OPTIONS, HIDDEN }

    data class Field(
        val name: String,
        val label: String,
        val value: String,
        val kind: Kind,
        val options: List<Pair<String, String>> = emptyList(),
    )

    data class Section(
        val title: String,
        val action: String,
        val method: String,
        val fields: List<Field>,
    )

    data class EditForm(val sections: List<Section>)

    suspend fun load(op: String = ""): EditForm? = withContext(Dispatchers.IO) {
        val URL = if (op.isBlank()) BASE_URL else "$BASE_URL&op=$op"
        runCatching {
            val html = GenericPageClient.get(URL)
            val doc = Jsoup.parse(html, URL)

            val sections = doc.select("form").filter { form ->
                val action = form.attr("action").lowercase()
                action.contains("spacecp") || action.isBlank() || action.contains("profile")
            }.mapNotNull { form ->
                val action = form.absUrl("action").ifBlank { URL }
                val method = form.attr("method").ifBlank { "post" }
                val title = form.closest("div, li, fieldset")
                    ?.selectFirst("h2, h3, .blocktitle, legend, .fname")?.text()?.trim()
                    ?: ""

                val fields = mutableListOf<Field>()

                fun labelOf(el: Element): String {
                    val id = el.attr("id")
                    if (id.isNotBlank()) {
                        val forLabel = form.selectFirst("label[for=$id]")?.text()?.trim()
                        if (!forLabel.isNullOrBlank()) return forLabel.trimEnd(':', '\uFF1A')
                    }
                    val holder = el.parents().firstOrNull {
                        it.tagName() in listOf("li", "tr", "dd")
                    }
                    val label = holder?.selectFirst("label, dt, th, span.fname, .fname")?.text()?.trim()
                    if (!label.isNullOrBlank() && label.length <= 20) return label.trimEnd(':', '\uFF1A')
                    val prev = el.parent()?.previousElementSibling()?.text()?.trim()
                    if (!prev.isNullOrBlank() && prev.length <= 20) return prev.trimEnd(':', '\uFF1A')
                    return el.attr("name")
                }

                form.select("input[name]").forEach { input ->
                    val type = input.attr("type").ifBlank { "text" }.lowercase()
                    val name = input.attr("name")
                    when (type) {
                        "hidden" -> fields += Field(name, "", input.attr("value"), Kind.HIDDEN)
                        "radio", "checkbox" -> {
                            if (fields.any { it.name == name }) return@forEach
                            val same = form.select("input[name=$name]")
                            val options = same.map { it.attr("value") to it.parent()?.text()?.trim().orEmpty() }
                            val checked = same.firstOrNull { it.hasAttr("checked") }?.attr("value").orEmpty()
                            fields += Field(name, labelOf(input), checked, Kind.OPTIONS, options)
                        }
                        "submit", "button", "file", "reset" -> {}
                        else -> fields += Field(name, labelOf(input), input.attr("value"), Kind.TEXT)
                    }
                }

                form.select("textarea[name]").forEach { ta ->
                    fields += Field(ta.attr("name"), labelOf(ta), ta.text(), Kind.TEXTAREA)
                }

                form.select("select[name]").forEach { sel ->
                    val options = sel.select("option").map { it.attr("value") to it.text().trim() }
                    val selected = sel.select("option[selected]").firstOrNull()?.attr("value")
                        ?: sel.select("option").firstOrNull()?.attr("value").orEmpty()
                    fields += Field(sel.attr("name"), labelOf(sel), selected, Kind.SELECT, options)
                }

                val visible = fields.filter {
                    it.kind != Kind.HIDDEN && it.name.isNotBlank() &&
                        it.name !in setOf("srchtxt", "mod", "searchsubmit", "formhash", "referer")
                }
                if (visible.isEmpty()) null
                else Section(title.ifBlank { "资料" }, action, method, fields)
            }

            if (sections.isEmpty()) null else EditForm(sections)
        }.getOrNull()
    }

    /** 逐个表单提交；返回是否至少有一个成功 */
    suspend fun submit(form: EditForm, values: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            var anyOk = false
            form.sections.forEach { section ->
                val ok = runCatching {
                    val merged = section.fields.associate { field ->
                        field.name to (values[field.name] ?: field.value)
                    }
                    val body = merged.entries.joinToString("&") {
                        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
                    }
                    val resp = if (section.method.equals("post", true)) {
                        GenericPageClient.httpPost(section.action, body, null)
                    } else {
                        GenericPageClient.get(
                            section.action + (if (section.action.contains("?")) "&" else "?") + body,
                        )
                    }
                    !resp.contains("\u8d85\u65f6") && !resp.contains("\u9519\u8bef")
                }.getOrDefault(false)
                if (ok) anyOk = true
            }
            anyOk
        }
}
