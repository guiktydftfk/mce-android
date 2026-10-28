package cn.mce.starlight.core

import cn.mce.starlight.data.ThreadSummary
import java.util.Calendar

/**
 * 首页「我的帖子」归位（v0.4.0 修正）。
 *
 * 背景：论坛「导读 → 最新」(`guide&view=newthread`) 有时**不收录自己发的帖子**（实测多次复现）,
 * 0.3.9 为了「刚发的帖子一定能看到」，把缺失的自己的帖子**无条件插到列表最前面**，
 * 副作用是：只要导读一直不收，它们就永远钉在首页顶端（用户反馈）。
 *
 * 现在改为按时间归位：
 *  - 从列表条目的相对时间（`3 分钟前` / `2 小时前` / `昨天 22:47` / `前天 13:06` / `2026-10-16`）解析大致时间；
 *  - 自己的帖子只在「仍在当前页时间范围之内」时才补进来，并插到时间对应的位置（刚发的会自然排到最前）；
 *  - 比当前页最老一条还旧的帖子不补（它本来就不属于"最新"这一页），避免又变成顶栏钉子户。
 */
object FeedTime {

    private val minuteAgoRegex = Regex("""(\d+)\s*分钟前""")
    private val hourAgoRegex = Regex("""(\d+)\s*小时前""")
    private val todayRegex = Regex("""今天\s*(\d{1,2})[:：](\d{2})""")
    private val yesterdayRegex = Regex("""昨天\s*(\d{1,2})[:：](\d{2})""")
    private val beforeYesterdayRegex = Regex("""前天\s*(\d{1,2})[:：](\d{2})""")
    private val absoluteDateRegex = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2})[:：](\d{2}))?""")

    /** 列表条目的相对时间（`span.y` 的 `时间 / N回复`），解析成大致毫秒时间戳；解析不了返回 null */
    fun parseApproxMs(metaText: String?, now: Long = System.currentTimeMillis()): Long? {
        val raw = metaText?.substringBefore("/")?.trim().orEmpty()
        if (raw.isBlank()) return null
        if (raw.contains("刚刚")) return now
        if (raw.contains("半小时前")) return now - 30L * 60 * 1000
        minuteAgoRegex.find(raw)?.let { return now - (it.groupValues[1].toLongOrNull() ?: 0L) * 60_000 }
        hourAgoRegex.find(raw)?.let { return now - (it.groupValues[1].toLongOrNull() ?: 0L) * 3_600_000 }

        todayRegex.find(raw)?.let { m ->
            return atDay(now, 0, m.groupValues[1].toIntOrNull() ?: 0, m.groupValues[2].toIntOrNull() ?: 0)
        }
        yesterdayRegex.find(raw)?.let { m ->
            return atDay(now, 1, m.groupValues[1].toIntOrNull() ?: 0, m.groupValues[2].toIntOrNull() ?: 0)
        }
        beforeYesterdayRegex.find(raw)?.let { m ->
            return atDay(now, 2, m.groupValues[1].toIntOrNull() ?: 0, m.groupValues[2].toIntOrNull() ?: 0)
        }
        absoluteDateRegex.find(raw)?.let { m ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            cal.set(
                m.groupValues[1].toIntOrNull() ?: 1970,
                (m.groupValues[2].toIntOrNull() ?: 1) - 1,
                m.groupValues[3].toIntOrNull() ?: 1,
                m.groupValues.getOrNull(4)?.toIntOrNull() ?: 0,
                m.groupValues.getOrNull(5)?.toIntOrNull() ?: 0,
                0,
            )
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        return null
    }


    private fun atDay(now: Long, daysAgo: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * 把 [extras]（导读里缺失的自己的帖子）按时间插回 [base] 的正确位置。
     * - 只补「时间不早于当前页最老一条」的帖子（否则它不属于最新一页）；
     * - 时间解析失败的 extras 不补，避免又变成顶部钉子户；
     * - [base] 原有顺序保持不动。
     */
    fun mergeExtras(
        base: List<ThreadSummary>,
        extras: List<ThreadSummary>,
        now: Long = System.currentTimeMillis(),
    ): List<ThreadSummary> {
        if (extras.isEmpty()) return base
        val baseTimes = base.map { parseApproxMs(it.metaText, now) }
        val oldest = baseTimes.filterNotNull().minOrNull()
        val merged = base.toMutableList()
        extras.forEach { extra ->
            if (merged.any { it.id == extra.id }) return@forEach
            val t = parseApproxMs(extra.metaText, now) ?: return@forEach
            if (oldest != null && t < oldest) return@forEach
            // 插到第一条「比它旧」的条目之前（列表按时间从新到旧）
            val index = merged.indexOfFirst { existing ->
                val et = parseApproxMs(existing.metaText, now)
                et != null && et < t
            }
            if (index >= 0) merged.add(index, extra) else merged.add(extra)
        }
        return merged
    }
}
