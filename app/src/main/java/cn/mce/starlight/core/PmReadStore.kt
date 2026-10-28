package cn.mce.starlight.core

import android.content.Context
import cn.mce.starlight.data.PersonalClient

/**
 * 私信「本地已读」记录（v0.3.7）。
 *
 * 背景：Discuz 手机模板本身对私信已读的处理不可靠 —— 在 APP 里点进会话读完消息后，
 * 服务端私信列表仍然带 `span.num` 未读标记（网页端也有同样现象，属论坛侧问题）。
 * 与其等论坛修，客户端自己兜一层：打开过某个会话后，把「该会话最后一条消息的签名」
 * 记到本地；下次拉列表时，签名没变就把未读数归零（角标/红点/提示条一并不再出现）。
 * 一旦对方发来新消息，最后一条消息内容变化 → 签名不匹配 → 未读重新出现。
 */
object PmReadStore {
    private const val PREFS = "mce_pm_read"
    private const val KEY_PREFIX = "read_"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        // v0.4.0：私信已读签名含聊天内容，改为加密存储；旧明文后台迁移
        Thread { runCatching { migrateToEncrypted() } }.apply { isDaemon = true }.start()
    }

    /** 会话签名：最后一条消息的预览（新消息到达时会变）；预览为空时退回时间文本 */
    fun signature(item: PersonalClient.PmItem): String =
        item.preview?.trim().orEmpty().ifBlank { item.time?.trim().orEmpty() }.take(120)

    /** 打开会话时调用：记住该会话当前的签名，表示「这些消息我已看过」 */
    fun markRead(touid: Int, signature: String) {
        if (touid <= 0 || signature.isBlank()) return
        prefs()?.edit()?.putString(KEY_PREFIX + touid, CryptoBox.encrypt(signature))?.apply()
    }

    fun isRead(touid: Int, signature: String): Boolean {
        if (touid <= 0 || signature.isBlank()) return false
        return CryptoBox.decrypt(prefs()?.getString(KEY_PREFIX + touid, null)) == signature
    }

    /** 把本地已读叠加到服务端列表：本地已读的会话，未读数归零 */
    fun overlay(items: List<PersonalClient.PmItem>): List<PersonalClient.PmItem> =
        items.map { item ->
            if (item.unread > 0 && isRead(item.touid, signature(item))) item.copy(unread = 0) else item
        }

    /** 退出登录时清空，避免串号 */
    fun clear() {
        prefs()?.edit()?.clear()?.apply()
    }

    private fun migrateToEncrypted() {
        val p = prefs() ?: return
        val plain = p.all.filterValues { it is String && !CryptoBox.isEncrypted(it as String) }
        if (plain.isEmpty()) return
        val editor = p.edit()
        plain.forEach { (key, value) -> editor.putString(key, CryptoBox.encrypt(value as String)) }
        editor.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
