package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 任务中心解析：把「任务标题 + 描述 + 立即申请」成组抽取，
 * 而不是把页面拍平成一堆链接。
 */
object TaskClient {
    private const val BASE = "https://mce.msi.cn"

    data class TaskItem(
        val id: String,
        val title: String,
        val description: String?,
        val applyUrl: String,
        val status: String?,
    )

    suspend fun load(): List<TaskItem> = withContext(Dispatchers.IO) {
        val html = GenericPageClient.get("$BASE/home.php?mod=task")
        val doc = Jsoup.parse(html, "$BASE/home.php?mod=task")

        doc.select("a[href*=do=apply]").mapNotNull { a ->
            val href = a.attr("href")
            val id = Regex("""id=(\d+)""").find(href)?.groupValues?.get(1) ?: href
            val applyUrl = absolutize(href)

            // 向上找任务块：最近的包含标题的容器
            var container: Element? = a.parent()
            var depth = 0
            while (container != null && depth < 6) {
                val heading = container.selectFirst("h1, h2, h3, h4, .taskname, strong")
                if (heading != null && heading.text().trim().length >= 2) break
                container = container.parent()
                depth++
            }

            val box = container ?: a.parent() ?: doc.body()
            val heading = box.selectFirst("h1, h2, h3, h4, .taskname, strong")
            val title = heading?.text()?.trim()
                ?.takeIf { it.isNotBlank() && !it.contains("申请") }
                ?: "任务 $id"

            val desc = box.select("p, .taskdesc, .tasktext")
                .map { it.text().trim() }
                .firstOrNull { it.length in 4..200 && !it.contains("申请") }

            val status = box.selectFirst(".taskstatus, .status")
                ?.text()?.trim()?.takeIf { it.isNotBlank() }

            TaskItem(id = id, title = title, description = desc, applyUrl = applyUrl, status = status)
        }.distinctBy { it.id }
    }

    suspend fun apply(task: TaskItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            GenericPageClient.get(task.applyUrl)
            true
        }.getOrDefault(false)
    }

    private fun absolutize(href: String): String {
        val h = href.replace("&amp;", "&")
        return when {
            h.startsWith("//") -> "https:$h"
            h.startsWith("http") -> h
            h.startsWith("/") -> "$BASE$h"
            else -> "$BASE/$h"
        }
    }
}
