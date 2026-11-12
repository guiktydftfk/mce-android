package cn.mce.starlight.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

/**
 * v0.4.7：帖子分享卡片（本地生成 PNG，无第三方依赖）。
 *
 * 卡片包含：应用名 / 帖子标题 / 作者 / 版块 / 二维码（帖子链接）。
 * 二维码指向 https://mce.msi.cn/forum.php?mod=viewthread&tid=xxx：
 * - 装了本应用的用户扫码/点链接 → 系统会给出「用星光班打开」；
 * - 没装的用户 → 直接由浏览器打开帖子（符合「没装软件自动跳浏览器」）。
 */
object ShareCard {
    private const val BASE_URL = "https://mce.msi.cn"

    fun threadUrl(tid: Int): String = "$BASE_URL/forum.php?mod=viewthread&tid=$tid"

    fun create(
        ctx: Context,
        tid: Int,
        title: String,
        author: String,
        board: String,
        cover: Bitmap? = null,
    ): android.net.Uri? = runCatching {
        val w = 900
        val h = 1200
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#12121A"))

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // 顶部品牌
        p.color = Color.parseColor("#FFB86B")
        p.textSize = 34f
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText("MCE 星光班 · 校园论坛", 60f, 96f, p)

        // v0.4.7：帖子首图（有图时顶部放一张大图，更接近主流分享卡片）
        cover?.let { cb ->
            val dst = android.graphics.RectF(0f, 130f, w.toFloat(), 470f)
            c.drawBitmap(cb, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            val scrim = Paint()
            scrim.color = Color.parseColor("#55000000")
            c.drawRect(dst, scrim)
        }
        // 标题（自动换行，最多 4 行）
        p.typeface = Typeface.DEFAULT_BOLD
        p.color = Color.WHITE
        p.textSize = 52f
        val maxWidth = w - 120f
        var line = ""
        var y = if (cover != null) 520f else 210f
        var lines = 0
        for (ch in title) {
            val next = line + ch
            if (p.measureText(next) > maxWidth) {
                c.drawText(line, 60f, y, p)
                y += 66f
                lines++
                line = ch.toString()
                if (lines >= 4) break
            } else {
                line = next
            }
        }
        if (lines < 4 && line.isNotEmpty()) {
            c.drawText(line, 60f, y, p)
            y += 66f
        }

        // 作者 / 版块
        val meta = Paint(Paint.ANTI_ALIAS_FLAG)
        meta.color = Color.parseColor("#B0B0B8")
        meta.textSize = 32f
        c.drawText("作者：$author", 60f, y + 16f, meta)
        c.drawText("版块：$board", 60f, y + 66f, meta)

        // 二维码
        val url = threadUrl(tid)
        val matrix = QrCode.encode(url)
        if (matrix != null) {
            val box = 360f
            val left = (w - box) / 2f
            val top = if (cover != null) 840f else 700f
            val bg = Paint(Paint.ANTI_ALIAS_FLAG)
            bg.color = Color.WHITE
            c.drawRoundRect(left - 26f, top - 26f, left + box + 26f, top + box + 26f, 30f, 30f, bg)
            val cell = box / matrix.size
            val qp = Paint()
            qp.color = Color.BLACK
            for (row in matrix.indices) {
                for (col in matrix[row].indices) {
                    if (matrix[row][col]) {
                        c.drawRect(
                            left + col * cell, top + row * cell,
                            left + (col + 1) * cell, top + (row + 1) * cell, qp,
                        )
                    }
                }
            }
            val hint = Paint(Paint.ANTI_ALIAS_FLAG)
            hint.color = Color.parseColor("#8A8A94")
            hint.textSize = 28f
            c.drawText("扫码打开帖子 · 已安装「MCE 星光班」可直接跳转", 60f, 1130f, hint)
        } else {
            val hint = Paint(Paint.ANTI_ALIAS_FLAG)
            hint.color = Color.parseColor("#8A8A94")
            hint.textSize = 28f
            c.drawText(url, 60f, 1130f, hint)
        }

        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "thread_$tid.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
    }.getOrNull()

    /** v0.4.7：带帖子首图的分享卡片（异步加载封面后绘制） */
    suspend fun createWithCover(
        ctx: Context,
        tid: Int,
        title: String,
        author: String,
        board: String,
        coverUrl: String?,
    ): android.net.Uri? {
        if (coverUrl.isNullOrBlank()) return create(ctx, tid, title, author, board, null)
        val cover = runCatching {
            val req = coil.request.ImageRequest.Builder(ctx)
                .data(coverUrl)
                .size(800, 420)
                .allowHardware(false)
                .build()
            val res = coil.Coil.imageLoader(ctx).execute(req)
            val d = (res as? coil.request.SuccessResult)?.drawable ?: return@runCatching null
            val bw = d.intrinsicWidth.coerceAtLeast(1)
            val bh = d.intrinsicHeight.coerceAtLeast(1)
            val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            d.setBounds(0, 0, bw, bh)
            d.draw(cv)
            out
        }.getOrNull()
        return create(ctx, tid, title, author, board, cover)
    }
}
