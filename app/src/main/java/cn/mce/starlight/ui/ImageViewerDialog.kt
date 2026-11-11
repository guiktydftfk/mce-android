package cn.mce.starlight.ui

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.SuccessResult
import kotlinx.coroutines.launch

/**
 * v0.4.7：帖子图片全屏查看器
 * - 双指缩放、双击放大/还原
 * - 左右滑动切换同一帖子内的其它图片
 * - 长按：保存当前图片 / 保存本组全部图片 / 分享链接
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImageViewerDialog(
    urls: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, urls.lastIndex)) { urls.size }
    var savingAll by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f)),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.01f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                            }
                        }
                        .combinedClickable(onClick = {}, onLongClick = { showMenu = true }),
                ) {
                    AsyncImage(
                        model = urls[page],
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
                }
            }
            Text(
                if (urls.size > 1) {
                    "${pagerState.currentPage + 1}/${urls.size} · 左右滑动 · 双击放大 · 长按保存/分享"
                } else {
                    "双击放大 · 长按保存/分享"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
            )
            if (showMenu) {
                ImageMenuDialog(
                    url = urls[pagerState.currentPage],
                    ctx = ctx,
                    canSaveAll = urls.size > 1 && !savingAll,
                    onSave = { saveImage(ctx, urls[pagerState.currentPage], scope) },
                    onSaveAll = {
                        savingAll = true
                        scope.launch {
                            urls.forEach { saveImageSync(ctx, it) }
                            savingAll = false
                            Toast.makeText(ctx, "已保存 ${urls.size} 张图片", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = { showMenu = false },
                )
            }
        }
    }
}

/** v0.4.5：长按菜单（保存到相册 / 保存全部 / 分享链接） */
@Composable
private fun ImageMenuDialog(
    url: String,
    ctx: android.content.Context,
    canSaveAll: Boolean,
    onSave: () -> Unit,
    onSaveAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("图片操作") },
        text = { Text(if (url.length > 60) url.take(60) + "…" else url) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onDismiss(); onSave() }) { Text("保存到相册") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (canSaveAll) {
                    androidx.compose.material3.TextButton(onClick = { onDismiss(); onSaveAll() }) { Text("保存全部") }
                }
                androidx.compose.material3.TextButton(onClick = {
                    onDismiss()
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, url)
                    }
                    runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "分享图片链接")) }
                }) { Text("分享链接") }
            }
        },
    )
}

/** 同步保存一张图片（保存全部时逐张调用） */
private suspend fun saveImageSync(ctx: android.content.Context, url: String): Boolean = runCatching {
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

/** v0.4.4：长按保存图片到相册 */
private fun saveImage(ctx: android.content.Context, url: String, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        val ok = saveImageSync(ctx, url)
        Toast.makeText(ctx, if (ok) "已保存到相册" else "保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
    }
}
