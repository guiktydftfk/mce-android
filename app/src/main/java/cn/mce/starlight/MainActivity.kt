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
        SettingsStore.init(this)
        setContent {
            AppTheme(darkTheme = SettingsStore.darkMode) {
                MceApp()
            }
        }
    }
}
