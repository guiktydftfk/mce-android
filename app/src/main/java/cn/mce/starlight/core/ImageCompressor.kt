package cn.mce.starlight.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * v0.4.8：发帖 / 回帖图片压缩。
 *
 * 弱网下原图（几 MB）上传很慢，而且论坛附件上限有限；
 * 统一缩到最长边 ≤1600px、JPEG 质量 85 再上传（体积通常降到原来 1/5 左右）。
 * 解码失败（非标准图片/权限问题）时回退原字节，不阻断上传。
 */
object ImageCompressor {

    private const val MAX_DIMEN = 1600
    private const val QUALITY = 85
    private const val FALLBACK_MAX_BYTES = 8 * 1024 * 1024

    /** 返回压缩后的 JPEG 字节；无法解码时返回原始字节（超大文件仍拒绝） */
    fun compress(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val raw = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (raw.size <= 512 * 1024) return raw // 小图（表情/截图）不必重编码

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return raw.takeIf { it.size <= FALLBACK_MAX_BYTES }
        }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMEN) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return raw.takeIf { it.size <= FALLBACK_MAX_BYTES }

        val longest = max(decoded.width, decoded.height)
        val scaled = if (longest > MAX_DIMEN) {
            val ratio = MAX_DIMEN.toFloat() / longest
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        val bytes = out.toByteArray()
        return if (bytes.isNotEmpty()) bytes else raw
    }

    /** 上传用文件名（统一 jpg，避免服务端按扩展名拒绝） */
    fun jpegName(prefix: String): String = "${prefix}_${System.currentTimeMillis()}.jpg"
}
