package cn.mce.starlight.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「本日星光值新增」（v0.3.8 引入，v0.3.9 修正）。
 *
 * 论坛本身没有「今日新增星光值」这种数字（站内积分项只有赞赏值/社区值/…，签到奖励也是社区值），
 * 所以客户端用**本地日快照 diff** 近似：记录当天第一次看到的积分（= 星光值总量），
 * 之后显示「当天峰值 − 当天基准」= 今日新增；跨天自动把当前值记为新的基准。
 *
 * v0.3.9 修正（用户反馈「花掉积分后今日星光归零」）：
 *   旧实现直接用「当前积分 − 基准」，一旦兑换礼品 / 打赏扣了积分，currentTotal < base，
 *   代码会把基准重置成当前值 → 今日增量从正数直接掉成 0，看起来像「今天的星光白攒了」。
 *   现在额外记录当天**峰值**，增量 = 峰值 − 基准：花积分只会让总积分下降，不会抹掉当天已获得的增量。
 *   纯算法在 [StarlightDailyMath]（可单测），这里只负责读写 SharedPreferences。
 *
 * 注意：站点「每日星光值上限 / 本日已满」的规则尚未确认（需要网页版上真实的每日上限数值），
 * 确认前这里不做上限判定、也不硬编码常数。
 */
object StarlightDailyStore {
    private const val PREFS = "mce_starlight_daily"
    private const val KEY_DATE = "date"
    private const val KEY_BASE = "base"
    private const val KEY_PEAK = "peak"

    private var appContext: Context? = null
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 传入当前总积分，返回「今日新增」：
     * 跨天/首次调用 → 记录基准与峰值并返回 0；
     * 同一天 → 返回峰值 − 基准（花积分导致总分下降时不会把已获得的增量抹掉）。
     */
    fun deltaFor(currentTotal: Int): Int {
        val p = prefs() ?: return 0
        val today = dayFormat.format(Date())
        val savedDate = p.getString(KEY_DATE, null)
        val saved = savedDate?.let {
            StarlightDailyMath.State(
                date = it,
                base = p.getInt(KEY_BASE, 0),
                peak = p.getInt(KEY_PEAK, 0),
            )
        }
        val result = StarlightDailyMath.apply(saved, today, currentTotal)
        val next = result.state
        if (saved == null || saved != next) {
            p.edit()
                .putString(KEY_DATE, next.date)
                .putInt(KEY_BASE, next.base)
                .putInt(KEY_PEAK, next.peak)
                .apply()
        }
        return result.delta
    }

    /** 退出登录时清空，避免串号 */
    fun clear() {
        prefs()?.edit()?.clear()?.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
