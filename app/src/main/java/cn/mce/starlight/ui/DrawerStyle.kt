package cn.mce.starlight.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mce.starlight.R
import cn.mce.starlight.core.DiagStats
import cn.mce.starlight.core.SessionStore
import cn.mce.starlight.core.SettingsStore
import cn.mce.starlight.data.AuthClient
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.HttpForumRepository
import cn.mce.starlight.data.UpdateClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// v0.4.7：从 MceApp.kt 拆出的抽屉条目样式（DrawerSection/DrawerItem）

@Composable
internal fun DrawerSection(title: String) {
    // v0.4.7：一级标题——分隔线 + 主题色粗体，层级更明显
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 6.dp),
        ) {
            // v0.4.7：主题色竖条 + 加粗加大文字，一级标题一眼可辨
            androidx.compose.foundation.layout.Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

/**
 * 抽屉菜单项（v0.3.7 液态玻璃）：
 * 选中态不再用纯色 pill，而是半透明主题色渐变 + 白色边缘高光，渐变随 [sheen] 缓慢流动形成「液态玻璃」质感。
 * [sheen] 只在抽屉打开时由 MceApp 的无限动画驱动（0f 表示不动画）。
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun DrawerItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    sheen: Float = 0f,
    /** v0.4.1：拖动重点框时由浮层统一绘制高亮，条目自身不再画，避免重影 */
    showHighlight: Boolean = selected,
    /** v0.4.4：长按（侧栏板块用于收藏/取消收藏） */
    onLongClick: (() -> Unit)? = null,
    /** v0.4.7：二级条目（更小的字号/缩进，用于板块与子项） */
    secondary: Boolean = false,
    /** v0.4.7：板块分组标题（二级标题，与普通菜单项、三级板块明显区分） */
    isGroupHeader: Boolean = false,
    onClick: () -> Unit,
) {
    // v0.3.7 修订：尺寸对齐上一版 NavigationDrawerItem（56dp 高、胶囊形、左右各约 12dp 留白），
    // 只把「选中高亮」换成液态玻璃渐变，不再额外收窄每一行的宽度
    // v0.4.7：按抽屉宽度做等比缩放（以 246dp 设计宽度为基准），各机型视觉一致
    val drawerScale = ((androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.6f) / 246f).coerceIn(0.85f, 1.2f)
    val shape = RoundedCornerShape(28.dp)
    val selectedFraction by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "drawerItemSelected",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = (12.dp * drawerScale), vertical = 2.dp)
            .height((if (secondary) 40f else 56f).dp * drawerScale)
                .then(modifier)
                .then(
                    if (onLongClick == null) Modifier.clickable(onClick = onClick)
                    else Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
                ),
    ) {
        if (selectedFraction > 0.01f) {
            // 玻璃质感高亮层：随 sheen 横向流动的渐变 + 1dp 白色边缘
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f * selectedFraction),
                                Color.White.copy(alpha = 0.12f * selectedFraction),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f * selectedFraction),
                            ),
                            start = Offset(1600f * sheen - 300f, 0f),
                            end = Offset(1600f * sheen + 500f, 420f),
                        ),
                        shape = shape,
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.30f * selectedFraction),
                        shape = shape,
                    ),
            )
        }
        Text(
            text = label,
            // 三级条目更小更淡；二级条目保持 labelLarge 半粗
            style = when {
                secondary -> MaterialTheme.typography.bodyMedium
                isGroupHeader -> MaterialTheme.typography.bodyLarge
                else -> MaterialTheme.typography.labelLarge
            },
            // v0.4.7：菜单文字整体加粗（未选中 SemiBold）更醒目
            fontWeight = when {
                selected -> FontWeight.Bold
                secondary -> FontWeight.Normal
                else -> FontWeight.Bold
            },
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                secondary -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = ((if (secondary) 38 else 16).dp * drawerScale),
                    end = (16.dp * drawerScale),
                ),
        )
    }
}

