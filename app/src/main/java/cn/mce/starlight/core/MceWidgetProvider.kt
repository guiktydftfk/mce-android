package cn.mce.starlight.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import cn.mce.starlight.MainActivity
import cn.mce.starlight.R

/** v0.4.5：桌面小部件 —— 显示未读私信数，点按打开 APP；由 UnreadPollJob 每次检查后刷新。 */
class MceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context, 0)) }
    }

    companion object {
        fun updateUnread(context: Context, unread: Int) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(context) ?: return
                val ids = mgr.getAppWidgetIds(ComponentName(context, MceWidgetProvider::class.java))
                ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context, unread)) }
            }
        }

        private fun buildViews(context: Context, unread: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_mce)
            views.setTextViewText(
                R.id.widget_status,
                if (unread > 0) "有 $unread 条未读私信" else "暂无未读 · 点按打开",
            )
            val launch = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_status, pending)
            views.setOnClickPendingIntent(R.id.widget_action, pending)
            return views
        }
    }
}