package cn.mce.starlight.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.mce.starlight.data.Net
import cn.mce.starlight.data.PersonalClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private const val KEY_PM_SIG = "pm_signature"

    private var appContext: Context? = null

    /** 最近一次 refresh 算出的签名：markAllSeen / 显示提示后直接落盘，不用再发请求 */
    private var latestPmSig: String? = null
    private var latestNoticeSig: String? = null

    /**
     * 未读私信条数（v0.3.7 起 = 各会话 `span.num` 之和，即真实未读条数；
     * 旧实现是「未读会话数」，与 UI「N 条新私信」的文案对不上，同一人连发多条时数字偏小）。
     */
    var unreadPm by mutableIntStateOf(0)
        private set

    /** 有未读的会话数 */
    var pmConversations by mutableIntStateOf(0)
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

    /**
     * 把「刚拉到的私信列表」写回未读真源（v0.3.7）：
     * 消息页加载/刷新列表后立即调用 → 页签角标与列表行永远同源，
     * 修掉「列表已读没了、页签还挂着 (2)」的旧问题。
     */
    fun applyPmItems(items: List<PersonalClient.PmItem>) {
        unreadPm = items.sumOf { it.unread.coerceAtLeast(0) }
        pmConversations = items.count { it.unread > 0 }
    }

    /**
     * 启动/回到前台/刷新后调用：拉私信列表页 + 提醒页，更新未读并决定是否弹顶部提示。
     *
     * v0.3.7 修订（修「每次进入都显示有新消息」）：
     * 旧逻辑只要 `unreadPm > 0 || unreadNotices > 0` 就弹，而私信没有任何"已读过"基线，
     * 于是只要服务端还有未读，每次冷启动/切回前台都会重新弹一次。
     * 现在改为**签名比对**：只有「未读私信签名 / 首条提醒签名」相对上次基线发生变化才弹，
     * 弹过（或没有变化）立即把当前签名记为基线，下次进入不再重复提示；
     * 角标数字仍按服务端真实未读计算，供消息页展示。
     */
    suspend fun refresh(showBanner: Boolean = true): Unit = withContext(Dispatchers.IO) {
        // v0.3.7：私信与提醒两条请求并行，冷启动少一个往返等待
        val pmDeferred = async { runCatching { PersonalClient.loadPm(1) }.getOrNull() }
        val noticesDeferred = async { runCatching { PersonalClient.loadNotices(1) }.getOrNull() }
        val pm = pmDeferred.await()
        if (pm != null) {
            applyPmItems(pm)
            latestPmSig = pmSignature(pm)
        }
        val notices = noticesDeferred.await()
        if (notices != null) {
            latestNoticeSig = noticeSignature(notices.firstOrNull())
        }

        val prefs = prefs()
        val savedPm = prefs?.getString(KEY_PM_SIG, null)
        val savedNotice = prefs?.getString(KEY_NOTICE_SIG, null)
        // 签名变化 = 相对上次已读真的有新消息；首次运行（没有基线）只记基线、不打扰
        // v0.3.7 补充：本地已读打开后未读会归零，签名从「有未读」变「空」属于"读完了"，
        // 不能当成新消息弹条，所以 pmIsNew 还要求当前确实还有未读
        val pmIsNew = latestPmSig != null && savedPm != null &&
            latestPmSig != savedPm && unreadPm > 0
        val noticeIsNew = latestNoticeSig != null && savedNotice != null && latestNoticeSig != savedNotice
        unreadNotices = if (noticeIsNew) 1 else 0

        if (showBanner && (pmIsNew || noticeIsNew)) {
            banner = Banner(
                pm = if (pmIsNew) unreadPm else 0,
                notices = if (noticeIsNew) 1 else 0,
            )
        }
        // 关键：把当前签名立刻记为基线 —— 顶部提示对同一批未读只出现一次
        persistSignatures()
        Unit
    }

    /** 未读私信签名：只取有未读的会话，uid+条数变化即视为「有新私信」 */
    private fun pmSignature(items: List<PersonalClient.PmItem>): String =
        items.asSequence()
            .filter { it.unread > 0 }
            .sortedBy { it.touid }
            .joinToString("|") { "${it.touid}:${it.unread}" }

    /** 提醒签名：首条提醒的时间+作者+摘要 */
    private fun noticeSignature(top: PersonalClient.NoticeItem?): String =
        top?.let { listOfNotNull(it.time, it.author, it.note?.take(40)).joinToString("|") }.orEmpty()

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
     * 注意：私信角标**不在这里清零**（v0.3.7 修订）—— 私信是否已读由服务端决定
     * （真正的原因是「读了会话」），清了会出现「角标 0、列表还有红点」的新错位；
     * 现在角标始终等于私信列表的未读条数，与列表同源。顶部提示的重复弹出由签名基线解决。
     */
    suspend fun markAllSeen() {
        unreadNotices = 0
        banner = null
        if (latestNoticeSig == null || latestPmSig == null) {
            // 还没 refresh 过（例如直接从抽屉进消息页）：补拉一次记录基线
            if (latestPmSig == null) {
                runCatching { PersonalClient.loadPm(1) }.getOrNull()?.let { latestPmSig = pmSignature(it) }
            }
            runCatching { PersonalClient.loadNotices(1) }.getOrNull()?.let {
                latestNoticeSig = noticeSignature(it.firstOrNull())
            }
        }
        persistSignatures()
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
        PmReadStore.clear()
        StarlightDailyStore.clear()
        prefs()?.edit()?.remove(KEY_NOTICE_SIG)?.remove(KEY_PM_SIG)?.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
