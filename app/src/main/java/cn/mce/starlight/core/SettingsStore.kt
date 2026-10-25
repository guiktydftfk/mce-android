package cn.mce.starlight.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 本地设置（SharedPreferences）：
 *   - 深色模式
 *   - 自定义背景图片（本地文件路径）
 *   - 背景透明度 / 模糊度
 */
object SettingsStore {
    private const val PREFS = "mce_settings"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_BG = "background_path"
    private const val KEY_BG_ALPHA = "background_alpha"
    private const val KEY_BG_BLUR = "background_blur"

    private var appContext: Context? = null

    var darkMode by mutableStateOf(false)
        private set

    var backgroundPath by mutableStateOf<String?>(null)
        private set

    var backgroundAlpha by mutableFloatStateOf(0.85f)
        private set

    var backgroundBlur by mutableFloatStateOf(0f)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        darkMode = prefs.getBoolean(KEY_DARK, false)
        backgroundPath = prefs.getString(KEY_BG, null)
        backgroundAlpha = prefs.getFloat(KEY_BG_ALPHA, 0.85f)
        backgroundBlur = prefs.getFloat(KEY_BG_BLUR, 0f)
    }

    fun updateDarkMode(enabled: Boolean) {
        darkMode = enabled
        prefs()?.edit()?.putBoolean(KEY_DARK, enabled)?.apply()
    }

    fun updateBackground(path: String?) {
        backgroundPath = path
        prefs()?.edit()?.putString(KEY_BG, path)?.apply()
    }

    fun updateBackgroundAlpha(value: Float) {
        backgroundAlpha = value.coerceIn(0.05f, 1f)
        prefs()?.edit()?.putFloat(KEY_BG_ALPHA, backgroundAlpha)?.apply()
    }

    fun updateBackgroundBlur(value: Float) {
        backgroundBlur = value.coerceIn(0f, 25f)
        prefs()?.edit()?.putFloat(KEY_BG_BLUR, backgroundBlur)?.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
