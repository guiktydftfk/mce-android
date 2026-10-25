package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt

/**
 * 沉浸式顶栏：悬浮在内容之上，白→透明渐变，内容可以从下方滑过。
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

/**
 * 可拖动刷新按钮：右下角起，可任意拖动，点一下刷新。
 */
@Composable
fun DraggableRefreshFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sizePx = with(density) { 56.dp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        var drag by remember { mutableStateOf(Offset.Zero) }
        val maxW = (constraints.maxWidth - sizePx - marginPx * 2).coerceAtLeast(0f)
        val maxH = (constraints.maxHeight - sizePx - marginPx * 2).coerceAtLeast(0f)

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        drag = Offset(
                            (drag.x + delta.x).coerceIn(-maxW, 0f),
                            (drag.y + delta.y).coerceIn(-maxH, 0f),
                        )
                    }
                },
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
        }
    }
}
