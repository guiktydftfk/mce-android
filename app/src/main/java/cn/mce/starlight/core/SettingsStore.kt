package cn.mce.starlight.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 本地设置（SharedPreferences）：
 *   - 深色模式
 *   - 自定义背景图片（本地路径）/ 透明度 / 模糊度
 *   - 自动更新：暂停到期时间、最近检查时间、已下载 APK 路径
 */
/**
 * 明暗模式（v0.3.7）：浅色 / 深色 / 跟随系统。
 * 旧版本只有一个布尔值（dark_mode），init 时会自动迁移。
 */
enum class DarkMode(val label: String, val key: String) {
    LIGHT("浅色", "LIGHT"),
    DARK("深色", "DARK"),
    SYSTEM("跟随系统", "SYSTEM");

    companion object {
        fun fromKey(key: String?): DarkMode? = entries.firstOrNull { it.key == key }
    }
}

object SettingsStore {
    private const val PREFS = "mce_settings"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_DARK_MODE = "dark_mode_setting"
    private const val KEY_ACCENT = "accent_theme"
    private const val KEY_BG = "background_path"
    private const val KEY_BG_ALPHA = "background_alpha"
    private const val KEY_BG_BLUR = "background_blur"
    private const val KEY_SNOOZE_UNTIL = "update_snooze_until"
    private const val KEY_LAST_CHECK = "update_last_check"
    private const val KEY_DOWNLOADED_APK = "update_downloaded_apk"
    private const val KEY_UI_SCALE = "ui_scale" // 已废弃（0.3.6 起并入 font_scale），仅用于清理
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_SIGNIN_REMIND = "signin_reminder_enabled"
    private const val KEY_SIGNIN_REMIND_HOUR = "signin_reminder_hour"
    private const val KEY_FEED_MIN_CHARS = "feed_min_chars"
    private const val KEY_URGENT_CODE = "urgent_handled_code"

    private var appContext: Context? = null

    var darkModeSetting by mutableStateOf(DarkMode.LIGHT)
        private set

    /** 主题色（AccentTheme 枚举名，默认 MONET = 原有莫奈蓝） */
    var accentTheme by mutableStateOf("MONET")
        private set

    var backgroundPath by mutableStateOf<String?>(null)
        private set

    var backgroundAlpha by mutableFloatStateOf(0.85f)
        private set

    var backgroundBlur by mutableFloatStateOf(0f)
        private set

    var updateSnoozeUntil by mutableLongStateOf(0L)
        private set

    var lastUpdateCheckAt by mutableLongStateOf(0L)
        private set

    var downloadedApkPath by mutableStateOf<String?>(null)
        private set

    var fontScale by mutableFloatStateOf(1f)
        private set
    var signinReminderEnabled by mutableStateOf(false)
        private set

    var signinReminderHour by mutableIntStateOf(20)
        private set

    /**
     * 首页帖子过滤（v0.3.8）：低于该字数的帖子不在首页信息流显示。
     * 0 = 关闭过滤；范围 0–200 字。
     */
    var feedMinChars by mutableIntStateOf(0)
        private set

    /** 已自动处理过的紧急更新版本号（同一版本只自动下载/拉起安装一次） */
    var urgentHandledCode by mutableIntStateOf(0)
        private set

    /** 首页是否按字数过滤 */
    val feedFilterEnabled: Boolean get() = feedMinChars > 0

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 明暗模式：优先读新键；旧版只有布尔 dark_mode（用户点过开关才会写），
        // 从没设置过的老用户 / 新安装用户默认「跟随系统」。
        darkModeSetting = DarkMode.fromKey(prefs.getString(KEY_DARK_MODE, null))
            ?: when {
                !prefs.contains(KEY_DARK) -> DarkMode.SYSTEM
                prefs.getBoolean(KEY_DARK, false) -> DarkMode.DARK
                else -> DarkMode.LIGHT
            }
        if (prefs.contains(KEY_DARK) || !prefs.contains(KEY_DARK_MODE)) {
            prefs.edit()
                .putString(KEY_DARK_MODE, darkModeSetting.key)
                .remove(KEY_DARK)
                ?.apply()
        }
        accentTheme = prefs.getString(KEY_ACCENT, "MONET") ?: "MONET"
        backgroundPath = prefs.getString(KEY_BG, null)
        backgroundAlpha = prefs.getFloat(KEY_BG_ALPHA, 0.85f)
        backgroundBlur = prefs.getFloat(KEY_BG_BLUR, 0f)
        updateSnoozeUntil = prefs.getLong(KEY_SNOOZE_UNTIL, 0L)
        lastUpdateCheckAt = prefs.getLong(KEY_LAST_CHECK, 0L)
        downloadedApkPath = prefs.getString(KEY_DOWNLOADED_APK, null)
        fontScale = prefs.getFloat(KEY_FONT_SCALE, 1f)
        // 0.3.6：界面大小与字体大小合并为单一「字体大小」，清掉旧键
        if (prefs.contains(KEY_UI_SCALE)) prefs.edit().remove(KEY_UI_SCALE)?.apply()
        signinReminderEnabled = prefs.getBoolean(KEY_SIGNIN_REMIND, false)
        signinReminderHour = prefs.getInt(KEY_SIGNIN_REMIND_HOUR, 20)
        feedMinChars = prefs.getInt(KEY_FEED_MIN_CHARS, 0).coerceIn(0, 200)
        urgentHandledCode = prefs.getInt(KEY_URGENT_CODE, 0)
    }

    fun updateDarkModeSetting(mode: DarkMode) {
        darkModeSetting = mode
        prefs()?.edit()?.putString(KEY_DARK_MODE, mode.key)?.apply()
    }

    fun updateAccentTheme(name: String) {
        accentTheme = name
        prefs()?.edit()?.putString(KEY_ACCENT, name)?.apply()
    }

    fun updateBackground(path: String?) {
        backgroundPath = path
        prefs()?.edit()?.putString(KEY_BG, path)?.apply()
    }

    /**
     * 更新背景透明度。
     * @param persist false 时只更新内存 state（拖动滑杆的实时预览），松手后再调一次 persist=true 落盘。
     *                旧实现 onValueChange 每帧都写一次 SharedPreferences（一次拖动 60+ 次整份 XML 重写）。
     */
    fun updateBackgroundAlpha(value: Float, persist: Boolean = true) {
        backgroundAlpha = value.coerceIn(0.05f, 1f)
        if (persist) prefs()?.edit()?.putFloat(KEY_BG_ALPHA, backgroundAlpha)?.apply()
    }

    /** 更新背景模糊度；persist 语义同 [updateBackgroundAlpha] */
    fun updateBackgroundBlur(value: Float, persist: Boolean = true) {
        backgroundBlur = value.coerceIn(0f, 25f)
        if (persist) prefs()?.edit()?.putFloat(KEY_BG_BLUR, backgroundBlur)?.apply()
    }

    fun snoozeUpdates(days: Int) {
        val until = System.currentTimeMillis() + days * 24L * 60 * 60 * 1000
        updateSnoozeUntil = until
        prefs()?.edit()?.putLong(KEY_SNOOZE_UNTIL, until)?.apply()
    }

    fun clearSnooze() {
        updateSnoozeUntil = 0L
        prefs()?.edit()?.putLong(KEY_SNOOZE_UNTIL, 0L)?.apply()
    }

    fun isUpdateSnoozed(): Boolean = updateSnoozeUntil > System.currentTimeMillis()

    fun snoozeRemainingDays(): Int {
        val left = updateSnoozeUntil - System.currentTimeMillis()
        if (left <= 0) return 0
        return ((left + 86_399_999L) / 86_400_000L).toInt()
    }

    fun markUpdateChecked() {
        lastUpdateCheckAt = System.currentTimeMillis()
        prefs()?.edit()?.putLong(KEY_LAST_CHECK, lastUpdateCheckAt)?.apply()
    }

    /** 字体缩放（0.8–1.4，即 80%–140%）；原「界面大小」滑杆 0.3.6 起已并入此项 */
    fun updateFontScale(value: Float) {
        fontScale = value.coerceIn(0.8f, 1.4f)
        prefs()?.edit()?.putFloat(KEY_FONT_SCALE, fontScale)?.apply()
    }

    fun updateSigninReminder(enabled: Boolean) {
        signinReminderEnabled = enabled
        prefs()?.edit()?.putBoolean(KEY_SIGNIN_REMIND, enabled)?.apply()
    }

    fun updateSigninReminderHour(hour: Int) {
        signinReminderHour = hour.coerceIn(0, 23)
        prefs()?.edit()?.putInt(KEY_SIGNIN_REMIND_HOUR, signinReminderHour)?.apply()
    }

    /** 标记某个版本号的紧急更新已自动处理过 */
    fun markUrgentHandled(code: Int) {
        urgentHandledCode = code
        prefs()?.edit()?.putInt(KEY_URGENT_CODE, code)?.apply()
    }

    /** 首页帖子过滤阈值（0 = 关闭；每 10 字一档，0–200） */
    fun updateFeedMinChars(value: Int, persist: Boolean = true) {
        feedMinChars = value.coerceIn(0, 200)
        if (persist) prefs()?.edit()?.putInt(KEY_FEED_MIN_CHARS, feedMinChars)?.apply()
    }

    fun updateDownloadedApk(path: String?) {
        downloadedApkPath = path
        prefs()?.edit()?.putString(KEY_DOWNLOADED_APK, path)?.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
