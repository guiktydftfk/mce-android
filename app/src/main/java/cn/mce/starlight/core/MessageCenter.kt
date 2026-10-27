package cn.mce.starlight.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.mce.starlight.data.PersonalClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 新消息中心（v0.3.4，v0.3.9 重做「弹一次」机制）。
 *
 * 需求：网页私信要能在「消息提醒」里看到；有新消息时在应用顶部弹提示，点它进消息页。
 *
 * Discuz 手机模板的可用信号（抓包确认）：
 *  - 私信未读：私信列表每条未读会话有 `span.num`
 *  - 提醒没有未读标记：改为「首条提醒的稳定键变化」判定是否有新提醒
 *
 * v0.3.9 修订（用户反馈「消息提醒弹窗不断弹出」）：
 *   旧实现每次启动 + 每次回前台都可能弹，且判定不稳：
 *    1. 提醒签名里含**相对时间**（"半小时前"→"1 小时前"），同一条提醒放一会儿就被当成新提醒；
 *    2. 私信签名是 `touid:未读数`，读掉其中一个会话后签名就变，也被当成新私信。
 *   现在改为：
 *    - **打开应用**（冷启动第一次 refresh）只要还有未读消息/新提醒，就弹**一次**（每次启动最多一次，靠
 *      [launchBannerShown] 保证）；
 *    - **有新消息**（私信会话内容签名 / 首条提醒稳定键相对基线变化）时再弹**一次**，弹完立即把新签名记为基线，
 *      同一批消息不会重复弹；
 *    - 纯判定逻辑抽到 [MessageNotifyRules]（可单测）。
 */
object MessageCenter {
    private const val PREFS = "mce_message"
    private const val KEY_NOTICE_SIG = "notice_signature"
    private const val KEY_PM_SIG = "pm_signature"

    private var appContext: Context? = null

    /** 最近一次 refresh 算出的基线：私信 = 未读会话的内容签名，提醒 = 首条提醒的稳定键 */
    private var latestPmSig: String? = null
    private var latestNoticeSig: String? = null

    /** 本次进程启动是否已经弹过「打开应用」提示（每次冷启动最多弹一次） */
    private var launchBannerShown = false

    /** 串行化 refresh / markAllSeen：冷启动时「启动刷新」与「回前台刷新」会并发 */
    private val refreshMutex = Mutex()

    /**
     * 未读私信条数（v0.3.7 起 = 各会话 `span.num` 之和，即真实未读条数；
     * 旧实现是「未读会话数」，与 UI「N 条新私信」的文案对不上，同一人连发多条时数字偏小）。
     */
    var unreadPm by mutableIntStateOf(0)
        private set

    /** 有未读的会话数 */
    var pmConversations by mutableIntStateOf(0)
        private set

    /** 未读提醒条数（论坛没有未读标记，用「是否有新提醒」近似：有新提醒 = 1） */
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

    /**
     * 把「刚拉到的私信列表」写回未读真源（v0.3.7）：
     * 消息页加载/刷新列表后立即调用 → 页签角标与列表行永远同源。
     */
    fun applyPmItems(items: List<PersonalClient.PmItem>) {
        unreadPm = items.sumOf { it.unread.coerceAtLeast(0) }
        pmConversations = items.count { it.unread > 0 }
    }

    /**
     * 启动 / 回到前台 / 刷新后调用：拉私信列表页 + 提醒页，更新未读并决定是否弹顶部提示。
     *
     * @param showBanner 是否允许弹提示（目前所有调用点都允许）
     * @param fromLaunch true = 应用冷启动后的第一次刷新：只要还有未读/新提醒就弹一次
     *                   （每次进程生命周期内最多一次；之后的刷新只对「真正的新消息」弹）
     *
     * v0.3.9：整段加锁串行化 —— 冷启动时「启动刷新」和「回前台刷新」可能并发，
     * 旧写法后跑完的那一路会用 0/0 把先弹出来的提示条覆盖成 null（表现为提示有时弹、有时不弹）。
     */
    suspend fun refresh(showBanner: Boolean = true, fromLaunch: Boolean = false): Unit =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock { doRefresh(showBanner, fromLaunch) }
        }

    private suspend fun doRefresh(showBanner: Boolean, fromLaunch: Boolean) = coroutineScope {
            // v0.3.7：私信与提醒两条请求并行，冷启动少一个往返等待
            val pmDeferred = async { runCatching { PersonalClient.loadPm(1) }.getOrNull() }
            val noticesDeferred = async { runCatching { PersonalClient.loadNotices(1) }.getOrNull() }

            val pm = pmDeferred.await()
            val curPmBaseline: Map<Int, Int>? = if (pm != null) {
                applyPmItems(pm)
                pmBaselineOf(pm).also { latestPmSig = MessageNotifyRules.encodeBaseline(it) }
            } else {
                null
            }
            val notices = noticesDeferred.await()
            if (notices != null) {
                latestNoticeSig = noticeStableKey(notices.firstOrNull())
            }

            val prefs = prefs()
            val savedPmSig = prefs?.getString(KEY_PM_SIG, null)
            val savedNoticeSig = prefs?.getString(KEY_NOTICE_SIG, null)

            // 私信：拉取失败时不做「新消息」判定，避免误报
            val pmIsNew = curPmBaseline != null &&
                MessageNotifyRules.pmHasNew(curPmBaseline, MessageNotifyRules.parseBaseline(savedPmSig))
            val noticeIsNew = MessageNotifyRules.noticeHasNew(latestNoticeSig, savedNoticeSig)
            unreadNotices = if (noticeIsNew) 1 else 0

            if (showBanner) {
                val isLaunchBanner = fromLaunch && !launchBannerShown
                if (fromLaunch) launchBannerShown = true
                val pmCount = when {
                    // 启动提示：把当前真实未读带上；日常刷新只提示「新增」
                    isLaunchBanner || pmIsNew -> unreadPm
                    else -> 0
                }
                val noticeCount = if (noticeIsNew) 1 else 0
                // 注意：只在确实有内容时才赋值。没有内容时保持现状，
                // 避免「并发刷新中另一路刚弹过的提示条被这路用 0/0 清掉」。
                if ((isLaunchBanner || pmIsNew || noticeIsNew) && (pmCount > 0 || noticeCount > 0)) {
                    banner = Banner(pmCount, noticeCount)
                }
            }

            // 关键：把当前签名立刻记为基线 —— 同一批消息（含「打开应用」那一次）只提示一次
            persistSignatures()
        }

    /** 私信基线：未读会话 → 最后一条消息内容签名（读到已读不算新消息，真来新内容才算） */
    private fun pmBaselineOf(items: List<PersonalClient.PmItem>): Map<Int, Int> =
        items.asSequence()
            .filter { it.unread > 0 }
            .associate { it.touid to PmReadStore.signature(it).hashCode() }

    private fun noticeStableKey(top: PersonalClient.NoticeItem?): String =
        top?.let { MessageNotifyRules.noticeKey(it.tid, it.author, it.note) }.orEmpty()

    private fun persistSignatures() {
        val p = prefs() ?: return
        val editor = p.edit()
        latestPmSig?.let { editor.putString(KEY_PM_SIG, it) }
        latestNoticeSig?.let { editor.putString(KEY_NOTICE_SIG, it) }
        editor.apply()
    }

    /**
     * 用户看过消息页后：清空「提醒」的未读与顶部提示，并把当前签名记为已读。
     *
     * 注意：私信角标**不在这里清零**（v0.3.7 修订）—— 私信是否已读由服务端 + [PmReadStore] 决定，
     * 清了会出现「角标 0、列表还有红点」的新错位；现在角标始终等于私信列表的未读条数。
     */
    suspend fun markAllSeen() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            unreadNotices = 0
            banner = null
            if (latestPmSig == null) {
                runCatching { PersonalClient.loadPm(1) }.getOrNull()?.let {
                    latestPmSig = MessageNotifyRules.encodeBaseline(pmBaselineOf(it))
                }
            }
            if (latestNoticeSig == null) {
                runCatching { PersonalClient.loadNotices(1) }.getOrNull()?.let {
                    latestNoticeSig = noticeStableKey(it.firstOrNull())
                }
            }
            persistSignatures()
        }
    }

    fun dismissBanner() {
        banner = null
    }

    fun reset() {
        unreadPm = 0
        pmConversations = 0
        unreadNotices = 0
        banner = null
        latestPmSig = null
        latestNoticeSig = null
        launchBannerShown = false
        PmReadStore.clear()
        StarlightDailyStore.clear()
        prefs()?.edit()?.remove(KEY_NOTICE_SIG)?.remove(KEY_PM_SIG)?.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
