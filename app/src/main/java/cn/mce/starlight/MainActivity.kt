package cn.mce.starlight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import cn.mce.starlight.core.SettingsStore
import cn.mce.starlight.ui.MceApp
import cn.mce.starlight.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                // 只保留「字体大小」一个滑杆（原「界面大小」已并入），不再缩放 dp
                LocalDensity provides Density(
                    density = base.density,
                    fontScale = base.fontScale * SettingsStore.fontScale,
                ),
            ) {
                AppTheme(darkTheme = SettingsStore.darkMode) {
                    MceApp()
                }
            }
        }
    }
}