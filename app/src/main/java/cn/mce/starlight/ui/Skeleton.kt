package cn.mce.starlight.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 骨架屏（v0.3.7 引入，v0.3.10 优化）。
 *
 * 旧实现把动画值读在**组合阶段**：每帧都重组整棵骨架树（首页 5 张卡片 ≈ 60+ 个块），
 * 加载瞬间明显掉帧。现在动画只在 **draw 阶段**读取进度，只重绘、不重组。
 */
@Composable
private fun rememberShimmerProgress(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
        ),
        label = "shimmerProgress",
    )
}

private fun Modifier.skeletonShimmer(
    progress: State<Float>,
    shape: Shape,
    base: Color,
): Modifier = this
    .clip(shape)
    .drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val band = w.coerceAtMost(420f)
        val x = -band + progress.value * (w + band * 2f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    base.copy(alpha = 0.42f),
                    base.copy(alpha = 0.92f),
                    base.copy(alpha = 0.42f),
                ),
                start = Offset(x, 0f),
                end = Offset(x + band, size.height.coerceAtLeast(1f)),
            ),
        )
    }

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    progress: State<Float>,
    base: Color,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(modifier.skeletonShimmer(progress, shape, base))
}

/** 帖子列表骨架：头像行 + 标题 + 两行摘要 + 大图占位 */
@Composable
fun ThreadListSkeleton(
    topPadding: Dp,
    horizontalPadding: Dp = 12.dp,
    count: Int = 5,
) {
    val progress = rememberShimmerProgress()
    val base = MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = horizontalPadding, end = horizontalPadding),
    ) {
        repeat(count) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBlock(Modifier.size(26.dp), progress, base, CircleShape)
                    SkeletonBlock(Modifier.width(96.dp).height(12.dp), progress, base)
                }
                SkeletonBlock(Modifier.fillMaxWidth(0.86f).height(16.dp), progress, base)
                SkeletonBlock(Modifier.fillMaxWidth().height(12.dp), progress, base)
                SkeletonBlock(Modifier.fillMaxWidth(0.62f).height(12.dp), progress, base)
                SkeletonBlock(Modifier.fillMaxWidth().height(150.dp), progress, base)
            }
        }
    }
}

/** 帖子详情骨架：标题 + 两层楼 */
@Composable
fun DetailSkeleton(topPadding: Dp) {
    val progress = rememberShimmerProgress()
    val base = MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().height(22.dp), progress, base)
        SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(12.dp), progress, base)
        repeat(2) {
            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBlock(Modifier.size(30.dp), progress, base, CircleShape)
                SkeletonBlock(Modifier.width(110.dp).height(12.dp), progress, base)
            }
            SkeletonBlock(Modifier.fillMaxWidth().height(12.dp), progress, base)
            SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(12.dp), progress, base)
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(12.dp), progress, base)
            SkeletonBlock(Modifier.fillMaxWidth().height(120.dp), progress, base)
        }
    }
}
