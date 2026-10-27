package cn.mce.starlight.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「本日星光值新增」（v0.3.8）。
 *
 * 论坛本身没有「今日新增星光值」这种数字（站内积分项只有赞赏值/社区值/…，签到奖励也是社区值），
 * 所以客户端用**本地日快照 diff** 近似：记录当天第一次看到的积分（= 星光值总量），
 * 之后显示「当前积分 − 当天基准」= 今日新增；跨天自动把当前值记为新的基准。
 */
object StarlightDailyStore {
    private const val PREFS = "mce_starlight_daily"
    private const val KEY_DATE = "date"
    private const val KEY_BASE = "base"

    private var appContext: Context? = null
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 传入当前总积分，返回「今日新增」：
     * 跨天/首次调用 → 记录基准并返回 0；同一天 → 返回 currentTotal − base（负数按 0 处理）。
     */
    fun deltaFor(currentTotal: Int): Int {
        if (currentTotal <= 0) return 0
        val p = prefs() ?: return 0
        val today = dayFormat.format(Date())
        val savedDate = p.getString(KEY_DATE, null)
        if (savedDate != today) {
            p.edit().putString(KEY_DATE, today).putInt(KEY_BASE, currentTotal).apply()
            return 0
        }
        val base = p.getInt(KEY_BASE, currentTotal)
        if (base <= 0 || base > currentTotal) {
            // 基准异常（清过数据/积分被扣）→ 重新记基准
            p.edit().putInt(KEY_BASE, currentTotal).apply()
            return 0
        }
        return currentTotal - base
    }

    /** 退出登录时清空，避免串号 */
    fun clear() {
        prefs()?.edit()?.clear()?.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
