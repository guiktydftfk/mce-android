package cn.mce.starlight.core

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * v0.4.4：后台每 15 分钟检查一次未读私信，有未读就发系统通知。
 * 只在已登录时发；读取时套用本地已读账本（避免已读会话又提醒）。
 *
 * v0.4.8：增加「提醒通知」（@ / 回复 / 系统），与私信通知分开开关；
 * 用最近一条提醒的内容+作者做去重键，避免同一提醒反复通知。
 */
class UnreadPollJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val ctx = applicationContext
        Thread {
            runCatching {
                if (SessionStore.isLoggedIn) {
                    val quiet = SettingsStore.notifyQuietEnabled && notifyQuietNow()

                    // 私信未读
                    val items = runBlocking { cn.mce.starlight.data.PersonalClient.loadPm(1) }
                    val effective = PmReadStore.overlay(items)
                    val unread = effective.sumOf { it.unread.coerceAtLeast(0) }
                    MceWidgetProvider.updateUnread(ctx, unread)
                    if (unread > 0 && SettingsStore.notifyUnreadEnabled && !quiet) {
                        val first = effective.firstOrNull { it.unread > 0 }
                        NotifyHelper.notifyUnread(ctx, unread, first?.touid)
                    } else {
                        NotifyHelper.cancel(ctx)
                    }

                    // v0.4.8：提醒通知（@ / 回复 / 系统）
                    if (SettingsStore.notifyNoticeEnabled && !quiet) {
                        val notices = runCatching {
                            runBlocking { cn.mce.starlight.data.PersonalClient.loadNotices(1) }
                        }.getOrNull()
                        val top = notices?.firstOrNull()
                        val key = top?.let { (it.note ?: "") + "|" + (it.author ?: "") }.orEmpty()
                        val prefs = ctx.getSharedPreferences("mce_unread", Context.MODE_PRIVATE)
                        val savedRaw = prefs.getString("notice_key", null)
                        val saved = CryptoBox.decrypt(savedRaw) ?: savedRaw
                        if (key.isNotBlank() && key != saved) {
                            NotifyHelper.notifyNotice(ctx, 1, top?.note?.take(60))
                            prefs.edit().putString("notice_key", CryptoBox.encrypt(key)).apply()
                        }
                    }
                }
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    private fun notifyQuietNow(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 23 || h < 7
    }

    companion object {
        private const val JOB_ID = 4201

        fun schedule(context: Context) {
            runCatching {
                val job = JobInfo.Builder(
                    JOB_ID,
                    ComponentName(context, UnreadPollJob::class.java),
                )
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * 60 * 1000L)
                    .build()
                context.getSystemService(JobScheduler::class.java)?.schedule(job)
            }
        }
    }
}
