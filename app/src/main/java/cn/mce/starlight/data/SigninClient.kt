package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * 签到（k_misign 插件）专用解析。
 * 触屏模板里签到按钮可能是链接或表单，这里都兼容。
 */
object SigninClient {
    private const val BASE = "https://mce.msi.cn"
    private const val URL_SIGN = "$BASE/plugin.php?id=k_misign:sign"

    data class State(
        val todayRank: String? = null,
        val continuous: String? = null,
        val total: String? = null,
        val alreadySigned: Boolean = false,
        val signActionUrl: String? = null,
        val signForm: PageBlock.Form? = null,
        val message: String? = null,
    )

    suspend fun load(): State = withContext(Dispatchers.IO) {
        val html = GenericPageClient.get(URL_SIGN)
        val doc = Jsoup.parse(html, URL_SIGN)
        val text = doc.text()

        fun pick(label: String): String? =
            Regex("""$label\s*[:：]?\s*(\d+)""").find(text)?.groupValues?.get(1)
                ?: Regex("""(\d+)\s*$label""").find(text)?.groupValues?.get(1)

        val todayRank = pick("今日排名")
        val continuous = pick("连续签到")
        val total = pick("累计签到")

        // 已签到判定：页面出现"已签到"/"今天已经签到"
        val alreadySigned = text.contains("已签到") || text.contains("今天已经签到")

        // 动作：优先找带 formhash / operation 的签到链接
        val actionLink = doc.select("a[href]").firstOrNull { a ->
            val href = a.attr("href")
            val t = a.text()
            (t.contains("签到") || t.contains("立即签到")) &&
                (href.contains("formhash") || href.contains("operation") || href.contains("k_misign"))
        }?.let { a ->
            absolutize(a.attr("href").replace("&amp;", "&"))
        }

        // 或者表单提交
        val form = doc.selectFirst("form")?.let { f ->
            val action = f.absUrl("action").ifBlank { URL_SIGN }
            val fields = f.select("input[name]")
                .filter { it.attr("type").lowercase() != "submit" }
                .map { it.attr("name") to it.attr("value") }
            val label = f.selectFirst("button, input[type=submit]")?.text()?.trim()
                ?.takeIf { it.isNotBlank() } ?: "签到"
            if (fields.isNotEmpty()) PageBlock.Form(action, f.attr("method").ifBlank { "get" }, fields, label) else null
        }

        State(
            todayRank = todayRank,
            continuous = continuous,
            total = total,
            alreadySigned = alreadySigned,
            signActionUrl = actionLink,
            signForm = form,
        )
    }

    suspend fun signIn(state: State): Boolean = withContext(Dispatchers.IO) {
        when {
            state.signForm != null -> GenericPageClient.submit(state.signForm)
            state.signActionUrl != null -> runCatching {
                GenericPageClient.get(state.signActionUrl)
                true
            }.getOrDefault(false)
            else -> false
        }
    }

    private fun absolutize(href: String): String = when {
        href.startsWith("//") -> "https:$href"
        href.startsWith("http") -> href
        href.startsWith("/") -> "$BASE$href"
        else -> "$BASE/$href"
    }
}
