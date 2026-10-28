package cn.mce.starlight.core

import cn.mce.starlight.data.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * v0.4.0：首页「我的帖子」按时间归位（修「自己的帖子永远钉在首页顶端」）。
 *
 * 旧行为：导读里缺失的自己的帖子被无条件 prepend 到列表最前 → 只要导读不收就一直置顶。
 * 新行为：解析列表条目的相对时间，把缺失的帖子插到时间对应位置；比当前页最老一条还旧的不补。
 */
class FeedTimeTest {

    /** 固定"现在"：2026-10-28 12:00:00（本地时区），避免测试依赖真实时间 */
    private val now: Long = run {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.OCTOBER, 28, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    private fun minutesAgo(min: Long) = now - min * 60_000

    private fun thread(id: Int, meta: String?) = ThreadSummary(
        id = id,
        title = "t$id",
        author = "我",
        avatarUrl = null,
        replies = 0,
        metaText = meta,
        typeLabel = null,
    )

    @Test
    fun parse_minutesAndHours() {
        assertEquals(minutesAgo(3), FeedTime.parseApproxMs("3 分钟前 / 5回复", now))
        assertEquals(now - 3_600_000, FeedTime.parseApproxMs("1 小时前 / 2回复", now))
        assertEquals(now - 2 * 3_600_000, FeedTime.parseApproxMs("2 小时前 / 0回复", now))
        assertEquals(now, FeedTime.parseApproxMs("刚刚 / 0回复", now))
    }

    @Test
    fun parse_yesterdayAndBeforeYesterday() {
        val y = FeedTime.parseApproxMs("昨天 22:47 / 6回复", now)!!
        val cal = Calendar.getInstance().apply { timeInMillis = y }
        assertEquals(27, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(22, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(47, cal.get(Calendar.MINUTE))

        val b = FeedTime.parseApproxMs("前天 13:06 / 17回复", now)!!
        val cal2 = Calendar.getInstance().apply { timeInMillis = b }
        assertEquals(26, cal2.get(Calendar.DAY_OF_MONTH))
        assertEquals(13, cal2.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun parse_absoluteDate() {
        val t = FeedTime.parseApproxMs("2026-10-16 / 24回复", now)!!
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(16, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parse_unknown_returnsNull() {
        assertEquals(null, FeedTime.parseApproxMs("", now))
        assertEquals(null, FeedTime.parseApproxMs(null, now))
        assertEquals(null, FeedTime.parseApproxMs("未知时间", now))
    }

    private fun base(): List<ThreadSummary> = listOf(
        thread(1, "10 分钟前 / 3回复"),
        thread(2, "2 小时前 / 5回复"),
        thread(3, "昨天 22:00 / 8回复"),
    )

    @Test
    fun merge_newestExtra_goesToTop() {
        val merged = FeedTime.mergeExtras(base(), listOf(thread(9, "1 分钟前 / 0回复")), now)
        assertEquals(listOf(9, 1, 2, 3), merged.map { it.id })
    }

    @Test
    fun merge_middleExtra_insertedByTime() {
        // 昨天 23:30 比「2 小时前」旧、比「昨天 22:00」新 → 应插在 id=3 之前
        val merged = FeedTime.mergeExtras(base(), listOf(thread(9, "昨天 23:30 / 0回复")), now)
        assertEquals(listOf(1, 2, 9, 3), merged.map { it.id })
    }

    @Test
    fun merge_olderThanPage_isSkipped() {
        // 前天 13:06 比当前页最老（昨天 22:00）还旧 → 不该补进来（否则又成顶部钉子户 / 错位）
        val merged = FeedTime.mergeExtras(base(), listOf(thread(9, "前天 13:06 / 0回复")), now)
        assertEquals(listOf(1, 2, 3), merged.map { it.id })
    }

    @Test
    fun merge_unparsableExtra_isSkipped() {
        val merged = FeedTime.mergeExtras(base(), listOf(thread(9, null)), now)
        assertEquals(listOf(1, 2, 3), merged.map { it.id })
    }

    @Test
    fun merge_neverDuplicates() {
        val merged = FeedTime.mergeExtras(base(), listOf(thread(2, "2 小时前 / 5回复")), now)
        assertEquals(listOf(1, 2, 3), merged.map { it.id })
        assertTrue(merged.size == 3)
    }

    @Test
    fun merge_keepsBaseOrderWhenNoExtras() {
        assertEquals(listOf(1, 2, 3), FeedTime.mergeExtras(base(), emptyList(), now).map { it.id })
    }
}
