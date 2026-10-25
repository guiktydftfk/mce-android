package cn.mce.starlight.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 本地设置（SharedPreferences） */
object SettingsStore {
    private const val PREFS = "mce_settings"
    private const val KEY_DARK = "dark_mode"

    private var appContext: Context? = null

    var darkMode by mutableStateOf(false)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        darkMode = prefs.getBoolean(KEY_DARK, false)
    }

    fun updateDarkMode(enabled: Boolean) {
        darkMode = enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_DARK, enabled)?.apply()
    }
}
