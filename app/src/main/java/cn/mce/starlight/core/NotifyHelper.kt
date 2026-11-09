package cn.mce.starlight.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** v0.4.4：未读提醒通知（系统通知 + 角标） */
object NotifyHelper {
    private const val CHANNEL_ID = "mce_unread"
    private const val NOTIFY_ID = 1001

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr?.getNotificationChannel(CHANNEL_ID) == null) {
                mgr?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "未读消息提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "新私信 / 回复提醒"
                    },
                )
            }
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun notifyUnread(context: Context, count: Int, touid: Int? = null) {
        if (count <= 0 || !canNotify(context)) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        if (touid != null && touid > 0) launch.putExtra("mce_open_pm", touid)
        val pending = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("MCE 星光班")
            .setContentText("你有 $count 条未读私信")
            .setNumber(count)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIFY_ID, notification)
        }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFY_ID) }
    }
}