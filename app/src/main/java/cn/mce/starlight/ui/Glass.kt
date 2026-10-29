package cn.mce.starlight.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * v0.4.1：类 iPhone「液态玻璃」素材（不需要底部导航栏）。
 *
 * 做法：半透明底色 + 顶部白色高光渐变 + 细边框 + 柔和阴影 + 按下缩放。
 * 不用 RenderEffect 逐帧模糊（性能代价大），靠 透明度/高光/描边 营造玻璃感。
 */
fun Modifier.iosGlass(
    corner: Int = 18,
    fillAlpha: Float = 0.55f,
    borderAlpha: Float = 0.22f,
): Modifier = composed {
    val shape = RoundedCornerShape(corner.dp)
    this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = fillAlpha))
        .background(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.35f to Color.White.copy(alpha = 0.02f),
                1f to Color.White.copy(alpha = 0f),
            ),
        )
        .border(1.dp, Color.White.copy(alpha = borderAlpha), shape)
}

/** 按压缩放（iOS 手感） */
fun Modifier.iosPressScale(): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "iosPressScale",
    )
    this.scale(scale)
}
