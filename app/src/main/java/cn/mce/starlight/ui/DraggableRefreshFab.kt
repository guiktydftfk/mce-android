package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 可拖动 + 可点击的刷新按钮。
 * 调用方用 Modifier.align(BottomEnd) 定位；自身只占按钮大小，不遮挡列表滚动。
 */
@Composable
fun DraggableRefreshFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .padding(18.dp)
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { },
                    onDragCancel = { },
                ) { change, delta ->
                    change.consume()
                    drag = Offset(
                        (drag.x + delta.x).coerceIn(-900f, 0f),
                        (drag.y + delta.y).coerceIn(-1600f, 0f),
                    )
                }
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "刷新",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(26.dp),
        )
    }
}
