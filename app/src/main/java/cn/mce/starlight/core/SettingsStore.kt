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
object SettingsStore {
    private const val PREFS = "mce_settings"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_ACCENT = "accent_theme"
    private const val KEY_BG = "background_path"
    private const val KEY_BG_ALPHA = "background_alpha"
    private const val KEY_BG_BLUR = "background_blur"
    private const val KEY_SNOOZE_UNTIL = "update_snooze_until"
    private const val KEY_LAST_CHECK = "update_last_check"
    private const val KEY_DOWNLOADED_APK = "update_downloaded_apk"
    private const val KEY_SIGNIN_REMIND = "signin_reminder_enabled"
    private const val KEY_SIGNIN_REMIND_HOUR = "signin_reminder_hour"

    private var appContext: Context? = null

    var darkMode by mutableStateOf(false)
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

    var signinReminderEnabled by mutableStateOf(false)
        private set

    var signinReminderHour by mutableIntStateOf(20)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        darkMode = prefs.getBoolean(KEY_DARK, false)
        accentTheme = prefs.getString(KEY_ACCENT, "MONET") ?: "MONET"
        backgroundPath = prefs.getString(KEY_BG, null)
        backgroundAlpha = prefs.getFloat(KEY_BG_ALPHA, 0.85f)
        backgroundBlur = prefs.getFloat(KEY_BG_BLUR, 0f)
        updateSnoozeUntil = prefs.getLong(KEY_SNOOZE_UNTIL, 0L)
        lastUpdateCheckAt = prefs.getLong(KEY_LAST_CHECK, 0L)
        downloadedApkPath = prefs.getString(KEY_DOWNLOADED_APK, null)
        signinReminderEnabled = prefs.getBoolean(KEY_SIGNIN_REMIND, false)
        signinReminderHour = prefs.getInt(KEY_SIGNIN_REMIND_HOUR, 20)
    }

    fun updateDarkMode(enabled: Boolean) {
        darkMode = enabled
        prefs()?.edit()?.putBoolean(KEY_DARK, enabled)?.apply()
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

    fun updateSigninReminder(enabled: Boolean) {
        signinReminderEnabled = enabled
        prefs()?.edit()?.putBoolean(KEY_SIGNIN_REMIND, enabled)?.apply()
    }

    fun updateSigninReminderHour(hour: Int) {
        signinReminderHour = hour.coerceIn(0, 23)
        prefs()?.edit()?.putInt(KEY_SIGNIN_REMIND_HOUR, signinReminderHour)?.apply()
    }

    fun updateDownloadedApk(path: String?) {
        downloadedApkPath = path
        prefs()?.edit()?.putString(KEY_DOWNLOADED_APK, path)?.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
