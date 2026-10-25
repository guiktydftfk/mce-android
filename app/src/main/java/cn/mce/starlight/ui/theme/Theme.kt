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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6FF1),
    onPrimary = Color.White,
    surface = Color(0xFFF7F8FA),
    background = Color(0xFFF7F8FA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AB0FF),
    onPrimary = Color(0xFF0B1B33),
    surface = Color(0xFF121316),
    background = Color(0xFF0E0F11),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
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
