package cn.mce.starlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.content.ContentValues
import androidx.core.graphics.drawable.toBitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import coil.compose.AsyncImage
import coil.request.SuccessResult
import kotlinx.coroutines.launch

/** v0.4.4：帖子图片全屏查看器（从 ThreadDetailScreen 拆出） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImageViewerDialog(url: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.96f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset = if (scale <= 1.01f) {
                            androidx.compose.ui.geometry.Offset.Zero
                        } else {
                            offset + pan
                        }
                    }
                }
                .combinedClickable(onClick = onDismiss, onLongClick = { showMenu = true }),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Text(
                "点按任意处关闭 · 双指缩放 · 长按保存/分享",
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
            )
            if (showMenu) {
                ImageMenuDialog(
                    url = url,
                    ctx = ctx,
                    onSave = { saveImage(ctx, url, scope) },
                    onDismiss = { showMenu = false },
                )
            }
        }
    }
}

/** v0.4.5：长按菜单（保存到相册 / 分享链接） */
@Composable
private fun ImageMenuDialog(url: String, ctx: android.content.Context, onSave: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("图片操作") },
        text = { Text(if (url.length > 60) url.take(60) + "…" else url) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onDismiss(); onSave() }) { Text("保存到相册") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                onDismiss()
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                }
                runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "分享图片链接")) }
            }) { Text("分享链接") }
        },
    )
}

/** v0.4.4：长按保存图片到相册（Android 10+ 走 MediaStore，10 以下写入应用图片目录并通知媒体库） */
private fun saveImage(ctx: android.content.Context, url: String, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        val ok = runCatching {
            // v0.4.5：按原图尺寸请求（不缩放），保存原图画质
            val request = coil.request.ImageRequest.Builder(ctx).data(url).size(coil.size.Size.ORIGINAL).build()
            val result = coil.Coil.imageLoader(ctx).execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: error("图片加载失败")
            val name = "MCE_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MCE")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法写入相册")
                ctx.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                } ?: error("写入失败")
            } else {
                val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: ctx.filesDir
                val file = java.io.File(dir, name)
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            }
        }.isSuccess
        Toast.makeText(ctx, if (ok) "已保存到相册（长按图片保存）" else "保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
    }
}