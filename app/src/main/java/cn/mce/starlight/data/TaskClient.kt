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
    private const val BASE = Net.BASE
    // v0.3.3 批 3：正则常量
    private val idRegex = Regex("""id=(\d+)""")
    private val hotRegex = Regex("""人气[:：]\s*(\d+)""")

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
            val id = idRegex.find(applyUrl)?.groupValues?.get(1) ?: applyUrl

            val titleLink = row.selectFirst("h3 a[href*=do=view]") ?: row.selectFirst("h3 a")
            val title = titleLink?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: "任务 $id"
            val detailUrl = titleLink?.let { absolutize(it.attr("href")) } ?: applyUrl

            val desc = row.selectFirst("p")?.text()?.trim()?.takeIf { it.isNotBlank() }
            val hot = hotRegex.find(row.text())?.groupValues?.get(1)

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

    /** 申请任务（v0.3.3 批 4：按响应判定，不再"GET 成功就算申请成功"） */
    suspend fun apply(task: TaskItem): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = GenericPageClient.get(task.applyUrl)
            DiscuzResponse.judge(
                resp,
                successText = "\u5df2\u63d0\u4ea4\u7533\u8bf7",
                unknownText = "\u63d0\u4ea4\u7ed3\u679c\u672a\u786e\u8ba4\uff0c\u8bf7\u5237\u65b0\u4efb\u52a1\u5217\u8868\u67e5\u770b",
            )
        }.getOrDefault(false to "\u7f51\u7edc\u9519\u8bef")
    }

    private fun absolutize(href: String): String = Net.absolutize(href)
}
