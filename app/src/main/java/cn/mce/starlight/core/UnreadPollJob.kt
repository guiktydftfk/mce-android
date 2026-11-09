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
 */
class UnreadPollJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val ctx = applicationContext
        Thread {
            runCatching {
                if (SessionStore.isLoggedIn) {
                    val items = runBlocking { cn.mce.starlight.data.PersonalClient.loadPm(1) }
                    val effective = PmReadStore.overlay(items)
                    val unread = effective.sumOf { it.unread.coerceAtLeast(0) }
                    MceWidgetProvider.updateUnread(ctx, unread)
                    val quiet = SettingsStore.notifyQuietEnabled && notifyQuietNow()
                    if (unread > 0 && SettingsStore.notifyUnreadEnabled && !quiet) {
                        val first = effective.firstOrNull { it.unread > 0 }
                        NotifyHelper.notifyUnread(ctx, unread, first?.touid)
                    } else {
                        NotifyHelper.cancel(ctx)
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