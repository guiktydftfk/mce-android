package cn.mce.starlight.core

/**
 * 星光「今日新增」纯逻辑（v0.3.9 抽出，不依赖 Android / SharedPreferences，方便单测）。
 *
 * 背景：论坛没有「今日星光新增」，客户端用本地日快照近似。
 * 旧实现增量 = 当前积分 − 当天基准；一旦兑换/打赏扣了积分，基准被重置 → 今日增量归零。
 * 现在记录当天**峰值**，增量 = 峰值 − 基准，花积分不会抹掉当天已获得的增量。
 */
object StarlightDailyMath {

    /** 落盘的一天快照：日期 + 当天基准（当天首次看到的积分）+ 当天峰值 */
    data class State(val date: String, val base: Int, val peak: Int)

    data class Result(val state: State, val delta: Int)

    /**
     * 用今天的日期与当前总积分推算出「今日新增」，并返回需要落盘的新快照。
     *
     * @param saved 已保存的快照（null = 从未记录）
     * @param today 今天（yyyy-MM-dd）
     * @param currentTotal 当前星光值总量
     */
    fun apply(saved: State?, today: String, currentTotal: Int): Result {
        // 积分还没加载出来（0/负数）：不动已有快照，显示 0
        if (currentTotal <= 0) return Result(saved ?: State(today, 0, 0), 0)

        // 跨天 / 首次：以当前值为新的基准与峰值
        if (saved == null || saved.date != today) {
            return Result(State(today, currentTotal, currentTotal), 0)
        }

        var base = saved.base
        var peak = saved.peak
        // 旧版本只写过 base（没有 peak）：把当前值补进峰值，升级当天不丢已有增量
        if (peak < base) peak = maxOf(base, currentTotal)
        if (base <= 0) {
            // 基准异常（清过数据等）：以当前值重新起算
            return Result(State(today, currentTotal, currentTotal), 0)
        }
        // 正常获得积分：抬高峰值（花积分导致的下降不抬高、也不降低峰值）
        if (currentTotal > peak) peak = currentTotal
        return Result(State(today, base, peak), (peak - base).coerceAtLeast(0))
    }
}
