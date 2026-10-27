package cn.mce.starlight.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.3.9：星光「今日新增」峰值逻辑回归。
 *
 * 用户反馈：兑换礼品 / 打赏后总积分下降，「今日星光」直接归零。
 * 修正后应满足：花积分不抹掉当天已获得的增量；只有跨天 / 首次观察才重置基准。
 */
class StarlightDailyMathTest {

    private val today = "2026-10-27"
    private fun st(base: Int, peak: Int) = StarlightDailyMath.State(today, base, peak)

    @Test
    fun firstObservationToday_recordsBaseAndPeak_returnsZero() {
        val r = StarlightDailyMath.apply(null, today, 1000)
        assertEquals(0, r.delta)
        assertEquals(1000, r.state.base)
        assertEquals(1000, r.state.peak)
        assertEquals(today, r.state.date)
    }

    @Test
    fun earnedPoints_deltaIsPeakMinusBase() {
        val r = StarlightDailyMath.apply(st(1000, 1000), today, 1012)
        assertEquals(12, r.delta)
        assertEquals(1012, r.state.peak)
    }

    @Test
    fun spentPoints_doesNotResetToZero() {
        // 今天先 +12（峰值 1012），随后兑换礼品扣了 5 分（当前 1007）
        val r = StarlightDailyMath.apply(st(1000, 1012), today, 1007)
        assertEquals(12, r.delta)
        assertEquals(1012, r.state.peak)
    }

    @Test
    fun spentBelowBase_stillKeepsEarnedDelta() {
        // 当前积分跌到基准以下（1000 → 990）：今日增量仍是 12，不能显示 0/负数
        val r = StarlightDailyMath.apply(st(1000, 1012), today, 990)
        assertEquals(12, r.delta)
    }

    @Test
    fun earnedAgainAfterSpending_extendsPeak() {
        // 峰值 1012，花掉后 1007，又赚到 1016：增量 = 1016 − 1000
        val r = StarlightDailyMath.apply(st(1000, 1012), today, 1016)
        assertEquals(16, r.delta)
        assertEquals(1016, r.state.peak)
    }

    @Test
    fun newDay_resetsBaseAndPeak() {
        val y = StarlightDailyMath.apply(StarlightDailyMath.State("2026-10-26", 900, 950), today, 1010)
        assertEquals(0, y.delta)
        assertEquals(1010, y.state.base)
        assertEquals(1010, y.state.peak)
    }

    @Test
    fun legacyRecordWithoutPeak_migratesAtCurrentValue() {
        // 旧版本只写了 base（peak 缺省 0）：沿用旧基准，把当前值补成峰值，不丢已有增量
        val r = StarlightDailyMath.apply(st(1000, 0), today, 1012)
        assertEquals(12, r.delta)
        assertEquals(1000, r.state.base)
        assertEquals(1012, r.state.peak)
    }

    @Test
    fun zeroOrNegativeTotal_doesNotTouchState() {
        val saved = st(1000, 1012)
        val r = StarlightDailyMath.apply(saved, today, 0)
        assertEquals(0, r.delta)
        assertEquals(saved, r.state)
    }
}
