package cn.mce.starlight

import android.os.Bundle
import androidx.activity.ComponentActivity
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
