package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.mce.starlight.core.SplashImageStore
import kotlinx.coroutines.delay

/**
 * v0.4.7：自定义启动页首图宿主。
 * 用 App 自身内容作为底，上面盖一层用户选择的图片（全屏裁剪），约 900ms 后移除。
 * 未设置启动图时完全透明、无额外开销。
 */
@Composable
fun CustomSplashHost(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val file = remember { SplashImageStore.current(ctx) }
    var visible by remember { mutableStateOf(file != null && !SplashImageStore.consumed) }

    Box(Modifier.fillMaxSize()) {
        content()
        if (visible && file != null) {
            LaunchedEffect(Unit) {
                delay(900)
                visible = false
                SplashImageStore.consumed = true
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = file,
                    contentDescription = "自定义启动页图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "MCE 星光班",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp),
                )
            }
        }
    }
}
