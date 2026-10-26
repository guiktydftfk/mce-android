package cn.mce.starlight.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 骨架屏（v0.3.7）。
 *
 * 旧实现所有页面加载都是「整屏白 + 居中转圈」，首屏要等 1~3 个网络往返，
 * 转圈一闪再闪回内容观感很差。这里用带流动高光的占位块（shimmer）代替，
 * 视觉上更像「内容正在长出来」，也比重绘全屏 RenderEffect 便宜。
 */
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
        ),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.45f),
            base.copy(alpha = 0.90f),
            base.copy(alpha = 0.45f),
        ),
        start = Offset(x, 0f),
        end = Offset(x + 420f, 420f),
    )
}

@Composable
private fun SkeletonBlock(modifier: Modifier, brush: Brush) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(brush),
    )
}

/** 帖子列表骨架：头像行 + 标题 + 两行摘要 + 大图占位 */
@Composable
fun ThreadListSkeleton(
    topPadding: Dp,
    horizontalPadding: Dp = 12.dp,
    count: Int = 5,
) {
    val brush = rememberShimmerBrush()
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
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(brush),
                    )
                    SkeletonBlock(Modifier.width(96.dp).height(12.dp), brush)
                }
                SkeletonBlock(Modifier.fillMaxWidth(0.86f).height(16.dp), brush)
                SkeletonBlock(Modifier.fillMaxWidth().height(12.dp), brush)
                SkeletonBlock(Modifier.fillMaxWidth(0.62f).height(12.dp), brush)
                SkeletonBlock(Modifier.fillMaxWidth().height(150.dp), brush)
            }
        }
    }
}

/** 帖子详情骨架：标题 + 两层楼 */
@Composable
fun DetailSkeleton(topPadding: Dp) {
    val brush = rememberShimmerBrush()
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().height(22.dp), brush)
        SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(12.dp), brush)
        repeat(2) {
            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(brush),
                )
                SkeletonBlock(Modifier.width(110.dp).height(12.dp), brush)
            }
            SkeletonBlock(Modifier.fillMaxWidth().height(12.dp), brush)
            SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(12.dp), brush)
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(12.dp), brush)
            SkeletonBlock(Modifier.fillMaxWidth().height(120.dp), brush)
        }
    }
}
