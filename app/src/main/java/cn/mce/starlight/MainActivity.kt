package cn.mce.starlight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.mce.starlight.core.SettingsStore
import cn.mce.starlight.ui.MceApp
import cn.mce.starlight.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // SettingsStore.init 已由 MceApplication.onCreate 完成；
        // 旧实现在这里又 init 一次，等于每次 Activity 创建/旋转都重新读一遍 prefs 并覆盖全部 Compose state。
        setContent {
            AppTheme(darkTheme = SettingsStore.darkMode) {
                MceApp()
            }
        }
    }
}
