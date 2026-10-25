package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 沉浸式顶栏：悬浮在内容之上，主题色→透明渐变，内容可从下方滑过。
 */
@Composable
fun ImmersiveTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val surface = MaterialTheme.colorScheme.surface
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(surface, surface.copy(alpha = 0.94f), Color.Transparent),
                ),
            )
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onMenu != null) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "菜单")
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onMenu == null) 12.dp else 0.dp),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
