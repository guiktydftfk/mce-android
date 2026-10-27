package cn.mce.starlight.core

/**
 * 消息提示的纯判定逻辑（v0.3.9 新增，不依赖 Android / 网络，方便单测）。
 *
 * 背景：旧实现用「签名串整体比较」判断是否有新消息，两个坑导致「消息提醒弹窗不断弹出」：
 *  1. 提醒签名里包含**相对时间**（"半小时前" → "1 小时前" → "昨天 13:06"），
 *     同一条提醒放一会儿就"变"成新提醒 → 每次回前台都弹一次。
 *  2. 私信签名是 `touid:未读数`，用户读掉其中一个会话后签名就变了，
 *     即使其余会话的未读没变化也会被判成「有新私信」。
 *
 * 现在：
 *  - 提醒用**稳定键**（tid / 作者 / 摘要，刻意不含相对时间）比较；
 *  - 私信用「会话内容签名（最后一条消息摘要的哈希）」基线比较：
 *    读到已读不算新、真正来了新内容才算新。
 */
object MessageNotifyRules {

    /** 私信基线编码：`touid:hash;touid:hash` */
    fun parseBaseline(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = HashMap<Int, Int>()
        raw.split(';').forEach { part ->
            val i = part.indexOf(':')
            if (i <= 0 || i >= part.length - 1) return@forEach
            val uid = part.substring(0, i).trim().toIntOrNull() ?: return@forEach
            val hash = part.substring(i + 1).trim().toIntOrNull() ?: return@forEach
            out[uid] = hash
        }
        return out
    }

    fun encodeBaseline(map: Map<Int, Int>): String =
        map.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

    /**
     * 是否有新私信：当前有未读的会话里，存在「基线里没有 / 内容签名不同」的会话。
     * 已读（会话未读归零而从当前集合消失）不算新消息。
     */
    fun pmHasNew(current: Map<Int, Int>, saved: Map<Int, Int>): Boolean =
        current.any { (uid, hash) -> saved[uid] != hash }

    /**
     * 首条提醒的稳定键：优先帖子 tid，其次作者 + 摘要。
     * 注意：**不含 time**（手机模板给的是相对时间，会随时间漂移，正是旧实现重复弹窗的根因）。
     */
    fun noticeKey(tid: Int?, author: String?, note: String?): String {
        val parts = buildList {
            tid?.let { add("tid:$it") }
            author?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            note?.trim()?.takeIf { it.isNotBlank() }?.let { add(it.take(40)) }
        }
        return parts.joinToString("|")
    }

    /**
     * 是否有新提醒：当前首条提醒的稳定键与基线不同。
     * 基线为空（首次运行 / 刚升级）时不算新消息，避免第一次启动就误弹。
     */
    fun noticeHasNew(currentKey: String?, savedKey: String?): Boolean =
        !currentKey.isNullOrBlank() && !savedKey.isNullOrBlank() && currentKey != savedKey
}
