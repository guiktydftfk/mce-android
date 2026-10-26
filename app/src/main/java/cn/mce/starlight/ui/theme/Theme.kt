package cn.mce.starlight.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cn.mce.starlight.core.SettingsStore
import coil.compose.AsyncImage

/**
 * 主题色（v0.3.3）。
 *
 * 默认保持原有的「莫奈蓝」配色（MOBILE 版一直用的那套），
 * 另外提供几种 Edge 风格的主题色，可在 设置 → 外观 → 主题色 切换。
 * 深色/浅色底与背景图逻辑保持不变，只替换 primary 系列。
 */
enum class AccentTheme(
    val label: String,
    val lightPrimary: Color,
    val lightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
) {
    MONET("莫奈蓝", Color(0xFF1B6FF1), Color(0xFFD8E6FF), Color(0xFF7AB0FF), Color(0xFF123C6E)),
    TEAL("青碧", Color(0xFF00897B), Color(0xFFC8F2EC), Color(0xFF4FD8C8), Color(0xFF004A44)),
    GREEN("松绿", Color(0xFF2E8B57), Color(0xFFCDEBD8), Color(0xFF6FD39A), Color(0xFF0E3B22)),
    PURPLE("鸢尾紫", Color(0xFF7A4DDE), Color(0xFFE7DDFF), Color(0xFFC0A8FF), Color(0xFF33206E)),
    ORANGE("落日橙", Color(0xFFE07B00), Color(0xFFFFE4C2), Color(0xFFFFB870), Color(0xFF5C2E00)),
    PINK("樱粉", Color(0xFFD81B60), Color(0xFFFFD9E4), Color(0xFFFF9BB5), Color(0xFF5E0B2A)),
    ;

    companion object {
        fun fromName(name: String?): AccentTheme =
            entries.firstOrNull { it.name == name } ?: MONET
    }
}

private val NeutralLightSurface = Color(0xFFF7F8FA)
private val NeutralDarkSurface = Color(0xFF121316)
private val NeutralDarkBackground = Color(0xFF0E0F11)
private val OnLightContainer = Color(0xFF1A1C1E)
private val OnDarkContainer = Color(0xFFEDF1F7)

private fun AccentTheme.lightScheme() = lightColorScheme(
    primary = lightPrimary,
    onPrimary = Color.White,
    primaryContainer = lightContainer,
    onPrimaryContainer = OnLightContainer,
    secondary = lightPrimary,
    surface = NeutralLightSurface,
    background = NeutralLightSurface,
)

private fun AccentTheme.darkScheme() = darkColorScheme(
    primary = darkPrimary,
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = darkContainer,
    onPrimaryContainer = OnDarkContainer,
    secondary = darkPrimary,
    surface = NeutralDarkSurface,
    background = NeutralDarkBackground,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val accent = AccentTheme.fromName(SettingsStore.accentTheme)
    val colors = if (darkTheme) accent.darkScheme() else accent.lightScheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = colors) {
        val background = SettingsStore.backgroundPath
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (background != null) {
                AsyncImage(
                    model = background,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(SettingsStore.backgroundAlpha)
                        .blur(SettingsStore.backgroundBlur.dp),
                )
            }
            Surface(
                color = if (background != null) Color.Transparent else MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        }
    }
}
