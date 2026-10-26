package cn.mce.starlight.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.mce.starlight.data.Net
import cn.mce.starlight.data.PersonalClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 新消息中心（v0.3.4）。
 *
 * 需求：网页私信要能在「消息提醒」里看到；有新消息时在应用顶部弹提示，点它进消息页。
 *
 * Discuz 手机模板的可用信号（抓包确认）：
 *  - 私信未读：任意页面底部导航 `a[href*=do=pm]` 带 `class="new"`；私信列表页每条未读会话有 `span.num`
 *  - 提醒没有未读标记：改为「首条提醒的签名（时间+作者+摘要）变化」判定是否有新提醒
 */
object MessageCenter {
    private const val PREFS = "mce_message"
    private const val KEY_NOTICE_SIG = "notice_signature"

    private var appContext: Context? = null

    /** 未读私信条数（Discuz 手机模板不返回总数，用带 num 的会话条数近似） */
    var unreadPm by mutableIntStateOf(0)
        private set

    /** 未读提醒条数（用"上次已读后新增的条数"近似） */
    var unreadNotices by mutableIntStateOf(0)
        private set

    /** 顶部弹窗数据；null 表示不显示 */
    var banner by mutableStateOf<Banner?>(null)
        private set

    data class Banner(val pm: Int, val notices: Int) {
        val total: Int get() = pm + notices
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 启动/回到前台/刷新后调用：拉私信列表页 + 提醒页，更新未读并决定是否弹顶部提示 */
    suspend fun refresh(showBanner: Boolean = true): Unit = withContext(Dispatchers.IO) {
        val pm = runCatching { PersonalClient.loadPm(1) }.getOrNull()
        if (pm != null) unreadPm = pm.count { it.unread > 0 }
        val notices = runCatching { PersonalClient.loadNotices(1) }.getOrNull()
        if (notices != null && notices.isNotEmpty()) {
            val top = notices.first()
            val sig = listOfNotNull(top.time, top.author, top.note?.take(40)).joinToString("|")
            val saved = prefs()?.getString(KEY_NOTICE_SIG, null)
            // 新增条数：签名相同 = 0；不同且已有记录 = 认为有一条新提醒（列表足够区分即可）
            unreadNotices = when {
                sig.isBlank() -> 0
                saved == null -> 0 // 首次启动不打扰
                saved == sig -> 0
                else -> 1
            }
        }
        if (showBanner && (unreadPm > 0 || unreadNotices > 0)) {
            banner = Banner(unreadPm, unreadNotices)
        }
        Unit
    }

    /** 用户看过消息页后：清空未读与顶部提示，并把当前提醒首条记为已读 */
    suspend fun markAllSeen() {
        unreadNotices = 0
        banner = null
        val notices = runCatching { PersonalClient.loadNotices(1) }.getOrNull()
        val top = notices?.firstOrNull()
        if (top != null) {
            val sig = listOfNotNull(top.time, top.author, top.note?.take(40)).joinToString("|")
            prefs()?.edit()?.putString(KEY_NOTICE_SIG, sig)?.apply()
        }
    }

    fun dismissBanner() {
        banner = null
    }

    fun reset() {
        unreadPm = 0
        unreadNotices = 0
        banner = null
        prefs()?.edit()?.remove(KEY_NOTICE_SIG)?.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
