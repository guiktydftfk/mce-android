package cn.mce.starlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 任务中心 —— 按接口逻辑实现：
 *   列表：GET home.php?mod=task
 *   每个任务：h3 > a[do=view&id=X] 标题；p 描述；a.taskbtn[do=apply&id=X] 申请
 *   详情：GET home.php?mod=task&do=view&id=X
 *   申请：GET home.php?mod=task&do=apply&id=X
 */
object TaskClient {
    private const val BASE = "https://mce.msi.cn"

    data class TaskItem(
        val id: String,
        val title: String,
        val description: String?,
        val reward: String?,
        val applyUrl: String,
        val detailUrl: String,
        val hot: String?,
    )

    suspend fun load(): List<TaskItem> = withContext(Dispatchers.IO) {
        val html = GenericPageClient.get("$BASE/home.php?mod=task")
        val doc = Jsoup.parse(html, "$BASE/home.php?mod=task")

        doc.select("a.taskbtn[href*=do=apply]").mapNotNull { btn ->
            val row: Element = btn.closest("tr") ?: btn.parent() ?: return@mapNotNull null
            val applyUrl = absolutize(btn.attr("href"))
            val id = Regex("""id=(\d+)""").find(applyUrl)?.groupValues?.get(1) ?: applyUrl

            val titleLink = row.selectFirst("h3 a[href*=do=view]") ?: row.selectFirst("h3 a")
            val title = titleLink?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: "任务 $id"
            val detailUrl = titleLink?.let { absolutize(it.attr("href")) } ?: applyUrl

            val desc = row.selectFirst("p")?.text()?.trim()?.takeIf { it.isNotBlank() }
            val hot = Regex("""人气[:：]\s*(\d+)""").find(row.text())?.groupValues?.get(1)

            val cells = row.select("td").map { it.text().trim() }.filter { it.isNotBlank() }
            val reward = cells.firstOrNull { it.contains("积分") }

            TaskItem(
                id = id,
                title = title,
                description = desc,
                reward = reward,
                applyUrl = applyUrl,
                detailUrl = detailUrl,
                hot = hot,
            )
        }.distinctBy { it.id }
    }

    suspend fun apply(task: TaskItem): Boolean = withContext(Dispatchers.IO) {
        runCatching { GenericPageClient.get(task.applyUrl); true }.getOrDefault(false)
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
