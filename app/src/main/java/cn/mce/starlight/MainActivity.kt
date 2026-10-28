package cn.mce.starlight

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import cn.mce.starlight.core.DarkMode
import cn.mce.starlight.core.SettingsStore
import cn.mce.starlight.ui.MceApp
import cn.mce.starlight.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    /** 按「设置里的明暗模式」解析当前是否深色（v0.4.0：启动窗口背景用它，避免暗色模式启动白屏） */
    private fun resolveDark(): Boolean {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return when (SettingsStore.darkModeSetting) {
            DarkMode.LIGHT -> false
            DarkMode.DARK -> true
            DarkMode.SYSTEM -> systemDark
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkNow = resolveDark()
        // v0.4.0：窗口背景与即将渲染的 Compose 主题背景保持一致（深色 #0E0F11 / 浅色 #F7F8FA）。
        // 系统深色（values-night）已覆盖「系统深色」场景，这里再兜住「应用设置深色但系统浅色」的情况。
        window.setBackgroundDrawable(
            ColorDrawable(if (darkNow) 0xFF0E0F11.toInt() else 0xFFF7F8FA.toInt()),
        )
        // 状态栏/导航栏图标明暗也按当前主题决定，避免启动瞬间白底黑图标或黑底黑图标
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { darkNow },
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { darkNow },
        )
        setContent {
            val base = LocalDensity.current
            // v0.3.7：明暗模式三态（浅色 / 深色 / 跟随系统）
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (SettingsStore.darkModeSetting) {
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
                DarkMode.SYSTEM -> systemDark
            }
            CompositionLocalProvider(
                // 只保留「字体大小」一个滑杆（原「界面大小」已并入），不再缩放 dp
                LocalDensity provides Density(
                    density = base.density,
                    fontScale = base.fontScale * SettingsStore.fontScale,
                ),
            ) {
                AppTheme(darkTheme = darkTheme) {
                    MceApp()
                }
            }
        }
    }
}
