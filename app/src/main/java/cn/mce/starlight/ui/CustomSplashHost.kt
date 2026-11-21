package cn.mce.starlight.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.mce.starlight.R
import cn.mce.starlight.core.SplashImageStore
import kotlinx.coroutines.delay

/**
 * v0.4.7：启动页宿主。
 *
 * v0.4.8 调整：
 *  - 用户设置过启动图 → 全屏展示自定义图片（约 900ms）；
 *  - 没有设置启动图 → 展示 **默认品牌启动页**（小胖星 logo + 名称 + 标语，约 520ms）。
 *    原因：Android 12+ 系统启动画面依赖 `windowSplashScreenAnimatedIcon`，但部分设备/模拟器
 *    （含 MuMu 等）不绘制系统启动图标，导致用户看不到任何启动 logo；这里在应用内兜底，
 *    所有机型都能看到一致的品牌启动页。
 */
@Composable
fun CustomSplashHost(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val file = remember { SplashImageStore.current(ctx) }
    var visible by remember { mutableStateOf(!SplashImageStore.consumed) }

    Box(Modifier.fillMaxSize()) {
        content()
        if (visible) {
            LaunchedEffect(Unit) {
                delay(if (file != null) 900 else 520)
                visible = false
                SplashImageStore.consumed = true
            }
            if (file != null) {
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
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_brand_star),
                            contentDescription = "MCE 星光班",
                            modifier = Modifier.size(132.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "MCE 星光班",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "赋能星未来，有爱星光班",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
