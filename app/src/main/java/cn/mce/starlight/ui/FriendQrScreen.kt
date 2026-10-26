package cn.mce.starlight.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.mce.starlight.core.SessionStore

/**
 * 我的好友码（v0.3.4）。
 *
 * 二维码内容是「我的主页链接」，好友用微信 / 系统相机 / 任意扫码器扫一下就能打开
 * 我的论坛主页并加好友；本应用「扫一扫」也会识别这个链接直接跳到对方主页。
 * 二维码由 [QrCode] 本地生成（无第三方依赖，可离线使用）。
 */
@Composable
fun FriendQrScreen(
    onMenu: () -> Unit,
    onOpenScan: () -> Unit,
) {
    val context = LocalContext.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val uid = SessionStore.uid
    val payload = remember(uid) {
        if (uid != null) "https://mce.msi.cn/home.php?mod=space&uid=$uid"
        else "https://mce.msi.cn/home.php"
    }
    val matrix = remember(payload) { QrCode.encode(payload) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = topInset + 70.dp, start = 24.dp, end = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "我的好友码",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                if (uid != null) "好友用微信 / 相机扫码即可打开你的主页加好友"
                else "登录后生成专属好友码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            if (matrix == null) {
                Text("二维码生成失败，请稍后重试", color = MaterialTheme.colorScheme.error)
            } else {
                QrCanvas(matrix)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                payload,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenScan,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("扫一扫加好友") }

            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("我的好友码链接", payload))
            }) { Text("复制好友链接") }

            Spacer(Modifier.height(10.dp))
            Text(
                "提示：对方扫到链接后用浏览器打开即可看到你的主页；" +
                    "本应用内「扫一扫」识别到好友码会直接跳到对方主页。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        ImmersiveTopBar(
            title = "我的好友码",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
            actionLabel = "扫一扫",
            onAction = onOpenScan,
        )
    }
}

@Composable
private fun QrCanvas(matrix: Array<BooleanArray>) {
    val modules = matrix.size
    Box(
        Modifier
            .size(260.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / modules
            for (row in 0 until modules) {
                for (col in 0 until modules) {
                    if (matrix[row][col]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(col * cell, row * cell),
                            size = Size(cell + 0.3f, cell + 0.3f),
                        )
                    }
                }
            }
        }
    }
}
