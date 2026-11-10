package cn.mce.starlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostFavoriteStoreTest {
    private fun item(tid: Int, title: String, group: String, note: String) =
        PostFavoriteStore.Item(tid, title, group, note, 1700000000000L)

    @Test
    fun encodeDecode_roundTrip() {
        val list = listOf(item(1, "帖子A", "学习", "稍后再看"), item(2, "帖子B", "默认分组", ""))
        val decoded = PostFavoriteStore.decode(PostFavoriteStore.encode(list))
        assertEquals(2, decoded.size)
        assertEquals("帖子A", decoded[0].title)
        assertEquals("学习", decoded[0].group)
        assertEquals("稍后再看", decoded[0].note)
    }

    @Test
    fun decode_skipsMalformedLines() {
        val text = "1|标题|分组|备注|1700000000000\nbadline\n2|x|g|n|notanumber"
        val decoded = PostFavoriteStore.decode(text)
        // 非法 at 按 0 处理但仍保留该条；非法行（字段不足）被跳过
        assertEquals(2, decoded.size)
        assertEquals(1, decoded[0].tid)
        assertEquals(0L, decoded[1].at)
    }

    @Test
    fun decode_blankGroupBecomesDefault() {
        val decoded = PostFavoriteStore.decode("3|标题||备注|1700000000000")
        assertEquals("默认分组", decoded[0].group)
    }

    @Test
    fun encode_escapesPipeInFields() {
        val encoded = PostFavoriteStore.encode(listOf(item(4, "标题|带竖线", "组", "备|注")))
        assertTrue(encoded.contains("标题｜带竖线"))
        assertEquals(1, PostFavoriteStore.decode(encoded).size)
    }
}
