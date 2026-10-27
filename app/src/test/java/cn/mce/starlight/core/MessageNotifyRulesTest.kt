package cn.mce.starlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.9：消息提示「只弹一次」判定回归。
 *
 * 旧实现的两个误判（用户反馈「消息提醒弹窗不断弹出」）：
 *  1. 提醒签名含相对时间（"半小时前" → "1 小时前"），同一条提醒会被反复当成新提醒；
 *  2. 私信签名是 `touid:未读数`，读掉一个会话后签名变化也会被当成新私信。
 * 这里覆盖新规则：提醒用不含时间的稳定键；私信用「未读会话内容签名」基线。
 */
class MessageNotifyRulesTest {

    // ---------- 提醒 ----------

    @Test
    fun noticeKey_sameNotice_isStable() {
        // 同一条提醒：作者/摘要/tid 不变 → 键不变（即使相对时间从「半小时前」变成「1 小时前」）
        val k1 = MessageNotifyRules.noticeKey(38027, "Billy", "您在主题 震撼美味 的帖子被 Billy 评分 赞赏值 +2")
        val k2 = MessageNotifyRules.noticeKey(38027, "Billy", "您在主题 震撼美味 的帖子被 Billy 评分 赞赏值 +2")
        assertEquals(k1, k2)
    }

    @Test
    fun noticeKey_differentNotice_differs() {
        val a = MessageNotifyRules.noticeKey(38027, "Billy", "评分 赞赏值 +2")
        val b = MessageNotifyRules.noticeKey(38028, "Billy", "评分 赞赏值 +2")
        assertTrue(a != b)
    }

    @Test
    fun noticeHasNew_noBaseline_isNotNew() {
        // 首次运行 / 刚升级：没有基线时不打扰（启动提示由「打开应用弹一次」承担）
        assertFalse(MessageNotifyRules.noticeHasNew("tid:1|Billy|abc", null))
        assertFalse(MessageNotifyRules.noticeHasNew("tid:1|Billy|abc", ""))
        assertFalse(MessageNotifyRules.noticeHasNew(null, "tid:1|Billy|abc"))
    }

    @Test
    fun noticeHasNew_keyChanged_isNew() {
        assertTrue(MessageNotifyRules.noticeHasNew("tid:2|Sera|新提醒", "tid:1|Billy|旧提醒"))
    }

    @Test
    fun noticeHasNew_keySame_isNotNew() {
        // 同一批提醒刷新多次（相对时间一直变）也只能算一次
        assertFalse(MessageNotifyRules.noticeHasNew("tid:1|Billy|旧提醒", "tid:1|Billy|旧提醒"))
    }

    // ---------- 私信 ----------

    @Test
    fun pmBaseline_roundTrip() {
        val map = mapOf(101 to 12345, 202 to -99887)
        val encoded = MessageNotifyRules.encodeBaseline(map)
        assertEquals(map, MessageNotifyRules.parseBaseline(encoded))
    }

    @Test
    fun pmBaseline_ignoresMalformedParts() {
        assertEquals(
            mapOf(101 to 5),
            MessageNotifyRules.parseBaseline("bad;101:5;:;202;:7;303:abc"),
        )
        assertEquals(emptyMap<Int, Int>(), MessageNotifyRules.parseBaseline(null))
    }

    @Test
    fun pmHasNew_sameUnreadContent_isNotNew() {
        // 同一批未读反复刷新 → 内容签名相同 → 不再弹
        val cur = mapOf(101 to 111)
        val saved = mapOf(101 to 111)
        assertFalse(MessageNotifyRules.pmHasNew(cur, saved))
    }

    @Test
    fun pmHasNew_newMessageContent_isNew() {
        // 同一会话来了新内容（预览签名变化）→ 新私信
        assertTrue(MessageNotifyRules.pmHasNew(mapOf(101 to 222), mapOf(101 to 111)))
    }

    @Test
    fun pmHasNew_newConversation_isNew() {
        assertTrue(MessageNotifyRules.pmHasNew(mapOf(101 to 111, 202 to 333), mapOf(101 to 111)))
    }

    @Test
    fun pmHasNew_readConversation_isNotNew() {
        // 读掉一个会话（未读归零、从当前集合消失）不算新私信
        assertFalse(MessageNotifyRules.pmHasNew(emptyMap(), mapOf(101 to 111)))
        assertFalse(MessageNotifyRules.pmHasNew(mapOf(202 to 222), mapOf(101 to 111, 202 to 222)))
    }
}
