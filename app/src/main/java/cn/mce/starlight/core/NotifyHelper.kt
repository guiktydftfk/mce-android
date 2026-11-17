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
    private const val CHANNEL_ID = "mce_pm"
    private const val CHANNEL_ID_NOTICE = "mce_notice"
    private const val NOTIFY_ID = 1001
    private const val NOTIFY_ID_NOTICE = 1002

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr?.getNotificationChannel(CHANNEL_ID) == null) {
                mgr?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "未读私信", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "新私信提醒"
                    },
                )
            }
            // v0.4.8：提醒（@ / 回复 / 系统）独立频道，可在系统设置里单独静音
            if (mgr?.getNotificationChannel(CHANNEL_ID_NOTICE) == null) {
                mgr?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID_NOTICE, "新提醒（@ / 回复 / 系统）", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "帖子回复、@ 提及与系统提醒"
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

    /** v0.4.8：提醒通知（@ / 回复 / 系统），与私信通知分开的开关 */
    fun notifyNotice(context: Context, count: Int, preview: String? = null) {
        if (count <= 0 || !canNotify(context) || !SettingsStore.notifyNoticeEnabled) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.putExtra("mce_open_notice", true)
        val pending = PendingIntent.getActivity(
            context, 1, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MCE 星光班 · 新提醒")
            .setContentText(preview ?: "你有 $count 条新提醒（@ / 回复 / 系统）")
            .setNumber(count)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIFY_ID_NOTICE, notification)
        }
    }

    fun cancelNotice(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFY_ID_NOTICE) }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFY_ID) }
    }
}
